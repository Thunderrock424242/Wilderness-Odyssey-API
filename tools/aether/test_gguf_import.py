"""Validate the version-pinned streaming Llama importer against Transformers."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def converter():
    spec = importlib.util.spec_from_file_location("gguf_import", Path(__file__).with_name("gguf_import.py"))
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


class RopeTest(unittest.TestCase):
    def test_llama31_frequency_vector_is_preserved_and_unknown_scaling_rejected(self):
        import torch
        from transformers import LlamaConfig
        from transformers.modeling_rope_utils import ROPE_INIT_FUNCTIONS
        config = LlamaConfig(hidden_size=4096, num_attention_heads=32, max_position_embeddings=131072,
                             rope_parameters={"rope_type":"default", "rope_theta":500000.0})
        factors = torch.ones(64)
        # Published Llama 3.1 frequency factors, including the transition band.
        factors[29:35] = torch.tensor([1.2074839,1.5534146,2.0263131,2.6945300,3.6842532,5.2573271])
        factors[35:] = 8
        converter().restore_rope(config, factors)
        inv, _ = ROPE_INIT_FUNCTIONS["llama3"](config, "cpu")
        base = 1 / (500000.0 ** (torch.arange(0,128,2).float()/128))
        self.assertTrue(torch.allclose(base/inv, factors, rtol=1e-5, atol=1e-5))
        with self.assertRaises(ValueError): converter().restore_rope(config, factors + 0.2)

    @unittest.skipUnless(os.environ.get("AETHER_VERIFICATION_GGUF"), "explicit small local GGUF required")
    def test_streamed_weights_match_official_loader_and_reload_logits(self):
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer
        source = Path(os.environ["AETHER_VERIFICATION_GGUF"])
        torch.set_num_threads(2)
        with tempfile.TemporaryDirectory(dir=ROOT / ".codex-build/aether-laptop") as temporary:
            output = Path(temporary) / "converted"
            converter().convert(source, output)
            reference = AutoModelForCausalLM.from_pretrained(source.parent, gguf_file=str(source),
                dtype=torch.float16, local_files_only=True, trust_remote_code=False)
            actual = AutoModelForCausalLM.from_pretrained(output, dtype=torch.float16,
                local_files_only=True, trust_remote_code=False, use_safetensors=True)
            for name, tensor in reference.state_dict().items():
                self.assertTrue(torch.equal(tensor, actual.state_dict()[name]), name)
            tokens = torch.tensor([[1,2,3,4]])
            with torch.no_grad():
                self.assertTrue(torch.equal(reference(tokens).logits, actual(tokens).logits))
            tokenizer = AutoTokenizer.from_pretrained(output, local_files_only=True, trust_remote_code=False)
            reference_tokenizer = AutoTokenizer.from_pretrained(source.parent, gguf_file=str(source),
                local_files_only=True, trust_remote_code=False)
            self.assertEqual(tokenizer.encode("Hello there!"), reference_tokenizer.encode("Hello there!"))
            self.assertEqual(tokenizer.chat_template, reference_tokenizer.chat_template)


if __name__ == "__main__": unittest.main()
