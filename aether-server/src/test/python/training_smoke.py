"""Real optimizer/artifact smoke, offline and deliberately separate from host admission.

Creates a tiny random local GPT-2 baseline solely to verify training mechanics.
This is not production Aether quality evidence, GPU evidence or Linux cgroup evidence.
Usage: python training_smoke.py <output-directory> [isolated-peft-library-directory]
"""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
sys.dont_write_bytecode = True

if len(sys.argv) > 2:
    sys.path.insert(0, str(Path(sys.argv[2]).resolve()))

root = Path(sys.argv[1]).resolve()
root.mkdir(parents=True, exist_ok=False)
source = Path(__file__).resolve().parents[2] / "main/resources/training/trainer.py"
spec = importlib.util.spec_from_file_location("aether_trainer", source)
trainer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trainer)

import torch
from transformers import GPT2Config, GPT2LMHeadModel, PreTrainedTokenizerFast
from tokenizers import Tokenizer
from tokenizers.models import WordLevel
from tokenizers.pre_tokenizers import Whitespace

torch.manual_seed(42)
base = root / "base"
base.mkdir()
words = ["[UNK]", "[EOS]", "Hello", "Aether", "Welcome", "traveler", "Who", "are", "you", "I", "am", "the", "guide"]
tokens = Tokenizer(WordLevel({w: i for i, w in enumerate(words)}, unk_token="[UNK]"))
tokens.pre_tokenizer = Whitespace()
tokenizer = PreTrainedTokenizerFast(tokenizer_object=tokens, eos_token="[EOS]", unk_token="[UNK]")
tokenizer.save_pretrained(base)
model = GPT2LMHeadModel(GPT2Config(vocab_size=len(words), n_positions=1024, n_ctx=1024,
                                n_embd=32, n_layer=1, n_head=2, eos_token_id=1, bos_token_id=1))
model.save_pretrained(base, safe_serialization=True)
baseline_hash = hashlib.sha256((base / "model.safetensors").read_bytes()).hexdigest()
dataset = json.dumps([
    {"id": "smoke-1", "revision": "reviewed-1", "prompt": "Hello Aether", "target": "Welcome traveler"},
    {"id": "smoke-2", "revision": "reviewed-1", "prompt": "Who are you", "target": "I am the guide"},
], ensure_ascii=False, separators=(",", ":"))
snapshot = root / "dataset.json"
snapshot.write_bytes(dataset.encode("utf-8"))
config = {"baseModelId": "tiny-gpt2:smoke", "modelDirectory": str(base), "presetId": "lora-smoke",
          "approvedBaseSha256": trainer.model_digest(base),
          "datasetPath": str(snapshot), "datasetHash": hashlib.sha256(snapshot.read_bytes()).hexdigest(),
          "outputDirectory": str(root / "candidate"), "cpuThreads": 2, "device": "cpu", "memoryMiB": 2048}
metadata = trainer.train(config)
assert metadata["steps"] == 8 and metadata["weightsChanged"] and metadata["loss"] > 0
assert baseline_hash == hashlib.sha256((base / "model.safetensors").read_bytes()).hexdigest()
from peft import PeftModel
reloaded = PeftModel.from_pretrained(GPT2LMHeadModel.from_pretrained(base, local_files_only=True), root / "candidate", local_files_only=True)
assert reloaded is not None
(root / "evidence.json").write_text(json.dumps({**metadata, "baseWeightsUnchanged": True,
    "candidateReloaded": True, "baseParameters": sum(p.numel() for p in model.parameters()),
    "candidateBytes": (root / "candidate/adapter_model.safetensors").stat().st_size}, indent=2), encoding="utf-8")
print((root / "evidence.json").read_text())
