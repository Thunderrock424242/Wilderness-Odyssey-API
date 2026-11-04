"""Official-source integrity and explicit text-only Qwen preparation policy."""
import hashlib
from pathlib import Path
import tempfile
import unittest
import zipfile
import stat
import importlib.util
import sys
from unittest.mock import patch


class QwenPreparationTest(unittest.TestCase):
    def test_conversion_disk_budget_includes_temporary_files_outside_published_output(self):
        from tools.aether import laptop
        original_popen=laptop.subprocess.Popen
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);state=root/"state"
            def worker(command,**options):
                config=Path(command[-1]);work=config.parent
                (work/"output").mkdir();(work/"output/model.gguf").write_bytes(b"GGUF")
                (work/"quantized").mkdir();(work/"quantized/overflow-marker").write_bytes(b"scratch")
                laptop.write_json(work/"success.json",{})
                return original_popen([sys.executable,"-I","-c","import time;time.sleep(2)"],**options)
            def disk_bytes(path):
                # Model a large conversion scratch allocation without allocating it.
                return 65*1073741824 if (path/"quantized/overflow-marker").exists() else 4
            with patch.object(laptop,"STATE",state),patch.object(laptop.subprocess,"Popen",side_effect=worker),patch.object(laptop,"observed_disk_bytes",side_effect=disk_bytes):
                with self.assertRaisesRegex(ValueError,"LOCAL_OPERATION_DISK_LIMIT"):
                    laptop.bounded_child("_convert_qwen",{"output":str(root/"published")},1024,10)
            self.assertFalse((root/"published").exists())

    def test_source_verification_rejects_same_path_substitution(self):
        from tools.aether import qwen35
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"weights";path.write_bytes(b"known weights")
            spec={"size":13,"sha256":hashlib.sha256(b"known weights").hexdigest()}
            qwen35.verify_download(path,spec)
            path.write_bytes(b"other weights")
            with self.assertRaisesRegex(ValueError,"SOURCE_HASH_MISMATCH"):qwen35.verify_download(path,spec)

    def test_git_blob_identity_includes_header_and_exact_bytes(self):
        from tools.aether import qwen35
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"LICENSE";path.write_bytes(b"License\n")
            spec={"size":8,"blobId":hashlib.sha1(b"blob 8\0License\n").hexdigest()}
            qwen35.verify_download(path,spec)
            path.write_bytes(b"License ")
            with self.assertRaises(ValueError):qwen35.verify_download(path,spec)

    def test_only_text_tensor_prefixes_are_admitted_and_exclusions_are_explicit(self):
        from tools.aether import qwen35
        self.assertEqual(qwen35.text_tensor_name("model.language_model.layers.0.linear_attn.A_log"),"model.layers.0.linear_attn.A_log")
        self.assertEqual(qwen35.text_tensor_name("lm_head.weight"),"lm_head.weight")
        self.assertIsNone(qwen35.text_tensor_name("model.visual.blocks.0.attn.qkv.weight"))
        self.assertIsNone(qwen35.text_tensor_name("mtp.layers.0.weight"))
        with self.assertRaisesRegex(ValueError,"UNEXPECTED_SOURCE_TENSOR"):qwen35.text_tensor_name("different_model.weight")

    def test_tool_archive_rejects_traversal_and_links_before_extracting(self):
        from tools.aether import qwen35
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            # ZipInfo normalizes Windows backslashes; use actual escaping names.
            for name in ("../escape.py","/absolute.py","C:/escape.py"):
                archive=root/"bad.zip"
                with zipfile.ZipFile(archive,"w") as stream:stream.writestr(name,b"no")
                with self.assertRaises(ValueError):qwen35.extract_tools(archive,root/"output")
                self.assertFalse((root/"escape.py").exists())
            with zipfile.ZipFile(archive,"w") as stream:
                link=zipfile.ZipInfo("link");link.create_system=3;link.external_attr=(stat.S_IFLNK|0o777)<<16
                stream.writestr(link,b"../outside")
            with self.assertRaisesRegex(ValueError,"LINKED_ARCHIVE_UNSUPPORTED"):qwen35.extract_tools(archive,root/"output")

    def test_qwen_training_prefix_explicitly_disables_thinking(self):
        from tools.aether import laptop
        observed=[]
        class Tokenizer:
            def apply_chat_template(self,messages,**options):
                observed.append(options.get("enable_thinking"))
                return [1,2] if options["add_generation_prompt"] else [1,2,3]
        prefix,target=laptop.core().chat_sample(Tokenizer(),"Q","A",system_prompt="Guide",enable_thinking=False)
        self.assertEqual((prefix,target),([1,2],[3]))
        self.assertEqual(observed,[False,False])

    def test_unreviewed_converter_manifest_is_rejected_before_execution(self):
        from tools.aether import qwen35
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/"toolchain.json"
            path.write_text('{"schemaVersion":"1.0","release":"unknown","files":{}}')
            with self.assertRaisesRegex(ValueError,"UNREVIEWED_CONVERTER"):
                qwen35.validate_tools(path)

    def test_qwen_finalization_rebinds_existing_merge_receipt_before_publication(self):
        from tools.aether import qwen35,laptop
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            laptop.write_json(root/"deployment.json",{"candidateName":"aether-candidate:test"})
            (root/"tensor").write_bytes(b"merged")
            laptop.write_manifest(root,{})
            (root/"model.gguf").write_bytes(b"GGUF converted")
            qwen35.finalize_candidate(root,{"ggufSha256":qwen35.sha(root/"model.gguf")})
            laptop.verify_manifest(root)
            self.assertEqual(laptop.read_json(root/"deployment.json")["ggufSha256"],qwen35.sha(root/"model.gguf"))
            self.assertFalse((root/"deployment.new.json").exists())


class QwenMergeTest(unittest.TestCase):
    def test_streaming_qwen_text_merge_matches_real_peft_forward(self):
        import torch
        import peft
        from transformers import Qwen3_5TextConfig,Qwen3_5ForCausalLM
        from tools.aether import merge_export
        from unittest.mock import patch
        torch.set_num_threads(2);torch.manual_seed(31)
        config=Qwen3_5TextConfig(vocab_size=64,hidden_size=32,intermediate_size=64,num_hidden_layers=4,
            num_attention_heads=2,num_key_value_heads=1,head_dim=16,
            linear_key_head_dim=8,linear_value_head_dim=8,linear_num_key_heads=2,linear_num_value_heads=2,
            layer_types=["linear_attention"]*3+["full_attention"],tie_word_embeddings=True,
            rope_parameters={"rope_type":"default","rope_theta":10000000,"partial_rotary_factor":.25,"mrope_section":[1,1,0]})
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);base=root/"base";candidate=root/"candidate"
            model=Qwen3_5ForCausalLM(config).half();model.save_pretrained(base)
            trained=peft.get_peft_model(model,peft.LoraConfig(task_type="CAUSAL_LM",r=4,lora_alpha=8,lora_dropout=0.0,target_modules="all-linear",bias="none"))
            with torch.no_grad():
                for name,param in trained.named_parameters():
                    if "lora_B" in name:param.fill_(.025)
            trained.eval();tokens=torch.tensor([[1,3,5,7]])
            with torch.no_grad():expected=trained(input_ids=tokens,attention_mask=torch.ones_like(tokens)).logits.float()
            trained.save_pretrained(candidate)
            # Tokenizer copying is external to this numerical tensor regression.
            class Tokenizer:
                chat_template="fixture"
                def save_pretrained(self,path):pass
            with patch("transformers.AutoTokenizer.from_pretrained",return_value=Tokenizer()):
                result=merge_export.merge(base,candidate,root/"merged")
            self.assertTrue(result["standaloneWeightsVerified"])
            merged=Qwen3_5ForCausalLM.from_pretrained(root/"merged",local_files_only=True).eval()
            with torch.no_grad():actual=merged(input_ids=tokens,attention_mask=torch.ones_like(tokens)).logits.float()
            self.assertTrue(torch.allclose(expected,actual,rtol=.01,atol=.01),f"max logit difference {float((expected-actual).abs().max())}")


if __name__=="__main__":unittest.main()
