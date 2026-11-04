"""Compare streamed export to a full PEFT safe merge using real local weights."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest


class CompatibilityTest(unittest.TestCase):
    def test_ollama_legacy_rope_fields_match_modern_transformers_fields(self):
        spec = importlib.util.spec_from_file_location("merge_export", Path(__file__).with_name("merge_export.py"))
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        parameters = {"rope_type":"llama3","rope_theta":500000.0,"factor":8.0,
                      "low_freq_factor":1.0,"high_freq_factor":4.0,"original_max_position_embeddings":8192}
        result = module.ollama_compatible_config({"rope_parameters":parameters})
        self.assertEqual(result["rope_theta"],500000.0)
        self.assertEqual(result["rope_scaling"],{k:v for k,v in parameters.items() if k != "rope_theta"})
        self.assertEqual(result["rope_parameters"],parameters)


@unittest.skipUnless(os.environ.get("AETHER_VERIFICATION_BASE") and os.environ.get("AETHER_VERIFICATION_ADAPTER"), "explicit local weights required")
class MergeTest(unittest.TestCase):
    def test_all_saved_tensors_and_standalone_logits_match_peft(self):
        import torch
        from peft import PeftModel
        from transformers import AutoModelForCausalLM
        spec = importlib.util.spec_from_file_location("merge_export", Path(__file__).with_name("merge_export.py"))
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        base = Path(os.environ["AETHER_VERIFICATION_BASE"]); candidate = Path(os.environ["AETHER_VERIFICATION_ADAPTER"])
        root = Path(__file__).resolve().parents[2] / ".codex-build/aether-laptop"
        torch.set_num_threads(2)
        with tempfile.TemporaryDirectory(dir=root) as temporary:
            output = Path(temporary) / "model"
            module.merge(base, candidate, output)
            original = AutoModelForCausalLM.from_pretrained(base, dtype=torch.float16, local_files_only=True, trust_remote_code=False)
            reference = PeftModel.from_pretrained(original, candidate, local_files_only=True).merge_and_unload(safe_merge=True)
            saved = AutoModelForCausalLM.from_pretrained(output, dtype=torch.float16, local_files_only=True, trust_remote_code=False)
            for name, value in reference.state_dict().items():
                self.assertTrue(torch.equal(value, saved.state_dict()[name]), name)
            with torch.no_grad():
                tokens = torch.tensor([[1,2,3,4]])
                self.assertTrue(torch.equal(reference(tokens).logits, saved(tokens).logits))


if __name__ == "__main__": unittest.main()
