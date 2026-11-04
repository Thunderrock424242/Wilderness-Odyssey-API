"""Explicit pinned Qwen acquisition and offline, lossless text-base preparation.

Network acquisition is an operator command, never a hosted training action.
All runtime/conversion steps consume verified local files, with no remote code.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import sys
import urllib.request
import zipfile
import time
import importlib.util
import subprocess


def require(valid, code):
    if not valid:raise ValueError(code)


def safe(path):
    path=Path(path).absolute()
    require(not str(path).startswith(("\\\\","//")),"LOCAL_PATH_REQUIRED")
    for part in (path,*path.parents):
        require(not part.is_symlink() and not (hasattr(part,"is_junction") and part.is_junction()),"LINKED_PATH")
    return path.resolve()


def sha(path):
    result=hashlib.sha256()
    with safe(path).open("rb") as stream:
        while chunk:=stream.read(1048576):result.update(chunk)
    return result.hexdigest()


def pins():
    return json.loads(Path(__file__).with_name("qwen35-source.json").read_bytes())


def verify_download(path,spec):
    path=safe(path)
    require(path.is_file() and path.stat().st_size==spec["size"],"SOURCE_SIZE_MISMATCH")
    if "sha256" in spec:
        require(sha(path)==spec["sha256"],"SOURCE_HASH_MISMATCH")
    else:
        result=hashlib.sha1(f"blob {spec['size']}\0".encode("ascii"))
        with path.open("rb") as stream:
            while chunk:=stream.read(1048576):result.update(chunk)
        require(result.hexdigest()==spec["blobId"],"SOURCE_HASH_MISMATCH")


def download(url,path,spec):
    path=safe(path)
    if path.exists():
        verify_download(path,spec)
        return
    path.parent.mkdir(parents=True,exist_ok=True)
    temporary=path.with_name(path.name+".download")
    require(not temporary.exists(),"PARTIAL_DOWNLOAD_REQUIRES_EXPLICIT_REMOVAL")
    try:
        size=0;reported=0;deadline=time.monotonic()+1800
        with urllib.request.urlopen(url,timeout=45) as response, temporary.open("xb") as stream:
            while chunk:=response.read(1048576):
                size+=len(chunk)
                require(size<=spec["size"],"SOURCE_SIZE_MISMATCH")
                require(time.monotonic()<=deadline,"DOWNLOAD_DEADLINE")
                stream.write(chunk)
                if size-reported>=512*1048576:
                    print(json.dumps({"file":path.name,"downloadedBytes":size}),flush=True);reported=size
            stream.flush();os.fsync(stream.fileno())
        verify_download(temporary,spec)
        temporary.rename(path)
    finally:temporary.unlink(missing_ok=True)


def extract_tools(archive,destination,selected=None):
    destination=safe(destination)
    require(not destination.exists(),"OUTPUT_ALREADY_EXISTS")
    with zipfile.ZipFile(safe(archive)) as stream:
        names=set();total=0
        for item in stream.infolist():
            name=item.filename
            require("\\" not in name and not name.startswith("/") and ":" not in name
                    and all(v not in ("..","") for v in name.rstrip("/").split("/")),"INVALID_ARCHIVE_PATH")
            require(name not in names,"DUPLICATE_ARCHIVE_PATH");names.add(name)
            require(stat.S_IFMT(item.external_attr>>16)!=stat.S_IFLNK,"LINKED_ARCHIVE_UNSUPPORTED")
            total+=item.file_size
            require(total<=1024*1048576,"ARCHIVE_SIZE_LIMIT")
        destination.mkdir(parents=True)
        for item in stream.infolist():
            if item.is_dir():continue
            relative=item.filename
            if selected is not None:
                relative=relative.split("/",1)[1] if "/" in relative else ""
                if not (relative in ("convert_hf_to_gguf.py","LICENSE") or relative.startswith(("conversion/","gguf-py/gguf/","requirements/"))):continue
            target=safe(destination/relative)
            require(destination in target.parents,"INVALID_ARCHIVE_PATH")
            target.parent.mkdir(parents=True,exist_ok=True)
            with stream.open(item) as src,target.open("xb") as dst:shutil.copyfileobj(src,dst,1048576)


def acquire(directory):
    directory=safe(directory);source=directory/"official-source";tools=directory/"toolchain"
    spec=pins()
    require(shutil.disk_usage(directory if directory.exists() else directory.parent).free>=40*1073741824,"ACQUISITION_DISK_CAPACITY")
    for name,record in spec["files"].items():
        require("/" not in name and "\\" not in name,"INVALID_SOURCE_NAME")
        print(json.dumps({"acquiring":name,"bytes":record["size"]}),flush=True)
        download(f"https://huggingface.co/{spec['repository']}/resolve/{spec['revision']}/{name}",source/name,record)
    converter=spec["converter"];downloads=directory/"downloads"
    archive=downloads/"llama-source.zip"
    download(f"https://codeload.github.com/ggml-org/llama.cpp/zip/{converter['commit']}",archive,
             {"size":converter["sourceBytes"],"sha256":converter["sourceSha256"]})
    binary=downloads/converter["binaryName"]
    download(f"https://github.com/ggml-org/llama.cpp/releases/download/{converter['release']}/{converter['binaryName']}",binary,
             {"size":converter["binaryBytes"],"sha256":converter["binarySha256"]})
    if not (tools/"source").exists():extract_tools(archive,tools/"source",selected=True)
    if not (tools/"bin").exists():extract_tools(binary,tools/"bin")
    files={p.relative_to(tools).as_posix():sha(p) for p in sorted(tools.rglob("*")) if p.is_file() and p.name!="toolchain.json"}
    require("source/convert_hf_to_gguf.py" in files and any(n.endswith("llama-quantize.exe") for n in files),"CONVERTER_TOOL_MISSING")
    manifest={"schemaVersion":"1.0","release":converter["release"],"commit":converter["commit"],
              "sourceArchiveSha256":converter["sourceSha256"],"binaryArchiveSha256":converter["binarySha256"],"files":files}
    (tools/"toolchain.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    return {"source":str(source),"toolchainManifest":str(tools/"toolchain.json"),"toolchainSha256":sha(tools/"toolchain.json")}


def text_tensor_name(name):
    if name.startswith("model.language_model."):return "model."+name.removeprefix("model.language_model.")
    if name=="lm_head.weight":return name
    if name.startswith(("model.visual.","mtp.")):return None
    raise ValueError("UNEXPECTED_SOURCE_TENSOR")


def prepare(source,output,toolchain_manifest):
    import torch
    from safetensors import safe_open
    from safetensors.torch import save_file,load_file
    from transformers import AutoConfig,AutoModelForCausalLM
    spec=pins();source=safe(source);output=safe(output)
    require(not output.exists() and source not in output.parents and output not in source.parents,"INVALID_OUTPUT")
    for name,record in spec["files"].items():verify_download(source/name,record)
    validate_tools(toolchain_manifest)
    full=AutoConfig.from_pretrained(source,local_files_only=True,trust_remote_code=False)
    require(full.model_type=="qwen3_5" and full.text_config.model_type=="qwen3_5_text","QWEN35_SOURCE_REQUIRED")
    config=full.text_config
    require(config.hidden_size==2560 and config.num_hidden_layers==32,"QWEN35_4B_REQUIRED")
    config.architectures=["Qwen3_5ForCausalLM"];config.mtp_num_hidden_layers=0;config._name_or_path=""
    with torch.device("meta"):model=AutoModelForCausalLM.from_config(config,trust_remote_code=False)
    expected={name:tuple(value.shape) for name,value in model.state_dict().items()}
    if config.tie_word_embeddings:expected.pop("lm_head.weight",None)
    require(shutil.disk_usage(output.parent).free>=24*1073741824,"PREPARATION_DISK_CAPACITY")
    torch.set_num_threads(2);output.mkdir()
    config.save_pretrained(output)
    for name in ("LICENSE","chat_template.jinja","tokenizer.json","tokenizer_config.json","vocab.json","merges.txt"):
        shutil.copyfile(source/name,output/name)
    index=json.loads((source/"model.safetensors.index.json").read_bytes())["weight_map"]
    inventory={};excluded={"vision":0,"mtp":0};total=0;source_keys=set()
    for shard in sorted(set(index.values())):
        require(shard in spec["files"] and "/" not in shard and "\\" not in shard,"INVALID_SHARD_NAME")
        with safe_open(source/shard,framework="pt",device="cpu") as opened:
            for name in opened.keys():
                require(name not in source_keys and index.get(name)==shard,"SOURCE_TENSOR_INDEX_MISMATCH");source_keys.add(name)
                mapped=text_tensor_name(name)
                if mapped is None:
                    excluded["vision" if name.startswith("model.visual.") else "mtp"]+=1
                    continue
                if mapped=="lm_head.weight" and config.tie_word_embeddings:
                    raise ValueError("UNEXPECTED_SEPARATE_TIED_HEAD")
                require(mapped in expected and mapped not in inventory,"UNEXPECTED_TEXT_TENSOR")
                tensor=opened.get_tensor(name)
                require(tuple(tensor.shape)==expected[mapped] and bool(torch.isfinite(tensor).all()),"INVALID_TEXT_TENSOR")
                filename=f"model-{len(inventory)+1:05d}.safetensors"
                save_file({mapped:tensor},output/filename,metadata={"format":"pt"})
                saved=load_file(output/filename)
                require(torch.equal(tensor,saved[mapped]),"TEXT_SHARD_VERIFICATION_FAILED")
                total+=tensor.numel()*tensor.element_size();inventory[mapped]=filename
                del tensor,saved
    require(source_keys==set(index) and set(inventory)==set(expected),"INCOMPLETE_TEXT_MODEL")
    (output/"model.safetensors.index.json").write_text(json.dumps({"metadata":{"total_size":total},"weight_map":inventory},indent=2),encoding="utf-8")
    provenance={"kind":"official-qwen35-text","repository":spec["repository"],"revision":spec["revision"],
                "textOnly":True,"excludedTensors":excluded,"textTensorCount":len(inventory),"textWeightBytes":total,
                "officialLicenseSha256":sha(output/"LICENSE"),
                "licenseSha256":hashlib.sha256((output/"LICENSE").read_text(encoding="utf-8").encode("utf-8")).hexdigest(),
                "toolchainManifestSha256":sha(toolchain_manifest),
                "officialFiles":{name:sha(source/name) for name in spec["files"]}}
    (output/"source.json").write_text(json.dumps(provenance,indent=2),encoding="utf-8")
    return {"base":str(output),"tensorCount":len(inventory),"excluded":excluded,"bytes":total}


def laptop_module():
    source=Path(__file__).with_name("laptop.py")
    spec=importlib.util.spec_from_file_location("qwen_local_engine",source)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module


def validate_tools(manifest_path,expected_hash=None):
    path=safe(manifest_path)
    require(path.is_file() and path.stat().st_size<=1048576,"CONVERTER_MANIFEST_LIMIT")
    if expected_hash is not None:require(sha(path)==expected_hash,"CONVERTER_MANIFEST_CHANGED")
    manifest=json.loads(path.read_bytes());pin=pins()["converter"]
    require(manifest.get("schemaVersion")=="1.0" and manifest.get("release")==pin["release"]
            and manifest.get("commit")==pin["commit"]
            and manifest.get("sourceArchiveSha256")==pin["sourceSha256"]
            and manifest.get("binaryArchiveSha256")==pin["binarySha256"],"UNREVIEWED_CONVERTER")
    root=path.parent;downloads=root.parent/"downloads"
    archives=((downloads/"llama-source.zip",pin["sourceSha256"],pin["sourceBytes"],"source",True),
              (downloads/pin["binaryName"],pin["binarySha256"],pin["binaryBytes"],"bin",False))
    expected={}
    for archive,digest,size,prefix,selected in archives:
        verify_download(archive,{"size":size,"sha256":digest})
        with zipfile.ZipFile(archive) as stream:
            for item in stream.infolist():
                if item.is_dir():continue
                relative=item.filename
                if selected:
                    relative=relative.split("/",1)[1] if "/" in relative else ""
                    if not (relative in ("convert_hf_to_gguf.py","LICENSE") or relative.startswith(("conversion/","gguf-py/gguf/","requirements/"))):continue
                key=prefix+"/"+relative
                require("\\" not in key and ":" not in key and all(v not in ("",".","..") for v in key.split("/")),"INVALID_CONVERTER_PATH")
                expected[key]=hashlib.sha256(stream.read(item)).hexdigest()
    require(manifest.get("files")==expected,"CONVERTER_INVENTORY_CHANGED")
    for name,digest in expected.items():
        require(sha(root/name)==digest,"CONVERTER_FILE_CHANGED")
    actual={p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file() and p!=path}
    require(actual==set(expected),"CONVERTER_FILE_SET_CHANGED")
    quantizers=[safe(root/name) for name in expected if name.endswith("/llama-quantize.exe")]
    require(len(quantizers)==1,"ONE_CPU_QUANTIZER_REQUIRED")
    return root/"source/convert_hf_to_gguf.py",quantizers[0]


QWEN_TEMPLATE='{{ range .Messages }}<|im_start|>{{ .Role }}\n{{ .Content }}<|im_end|>\n{{ end }}<|im_start|>assistant\n<think>\n\n</think>\n\n'


def modelfile(model_source,license_text):
    require('"""' not in license_text and "\0" not in license_text,"INVALID_SOURCE_LICENSE")
    return ('FROM "'+str(model_source)+'"\nTEMPLATE """'+QWEN_TEMPLATE+'"""\n'
            'PARAMETER stop "<|im_end|>"\nPARAMETER stop "<|endoftext|>"\n'
            'PARAMETER num_ctx 8192\nLICENSE """'+license_text+'"""\n')


def tool_process(command,environment):
    # Only called inside an inherited bounded Windows job. No shell or downloads.
    process=subprocess.Popen(command,env=environment,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,
                             stderr=subprocess.PIPE,creationflags=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0)
    tail=bytearray();total=0
    try:
        while chunk:=process.stderr.read(4096):
            total+=len(chunk);require(total<=16*1048576,"CONVERTER_DIAGNOSTIC_LIMIT")
            tail.extend(chunk);del tail[:-32768]
        code=process.wait()
        if code:
            frames=laptop_module().native_frames(tail.decode("utf-8",errors="replace"))
            print(json.dumps({"converterFailure":{"exitCode":code,"frames":frames}}),file=sys.stderr)
        require(code==0,"QWEN_CONVERSION_FAILED")
    finally:
        if process.poll() is None:process.kill()
        process.wait();process.stderr.close()


def convert_weights(model,toolchain_manifest,output):
    model=safe(model);output=safe(output)
    converter,quantizer=validate_tools(toolchain_manifest)
    config=json.loads((model/"config.json").read_bytes())
    require(config.get("model_type")=="qwen3_5_text" and config.get("hidden_size")==2560
            and config.get("num_hidden_layers")==32 and not config.get("auto_map"),"QWEN35_4B_TEXT_REQUIRED")
    tokenizer=json.loads((model/"tokenizer_config.json").read_bytes())
    require(not tokenizer.get("auto_map"),"REMOTE_TOKENIZER_UNSUPPORTED")
    require(not output.exists(),"OUTPUT_ALREADY_EXISTS");output.mkdir(parents=True)
    engine=laptop_module();environment=engine.child_environment(os.environ)
    environment.update(OMP_NUM_THREADS="2",MKL_NUM_THREADS="2",TOKENIZERS_PARALLELISM="false")
    raw=output/"model-f16.gguf";quantized=output/"model.gguf"
    # Isolated Python omits the script's directory; admit only the pinned tree.
    launch="import runpy,sys;from pathlib import Path;p=Path(sys.argv[1]);sys.path.insert(0,str(p.parent));sys.argv=sys.argv[1:];runpy.run_path(str(p),run_name='__main__')"
    tool_process([sys.executable,"-I","-B","-c",launch,str(converter),str(model),"--outfile",str(raw),"--outtype","f16",
                  "--no-mtp","--model-name","Aether Qwen 3.5 4B"],environment)
    tool_process([str(quantizer),str(raw),str(quantized),"Q4_K_M","2"],environment)
    require(quantized.is_file() and 1048576<quantized.stat().st_size<8*1073741824,"QWEN_GGUF_SIZE_LIMIT")
    with quantized.open("rb") as stream:require(stream.read(4)==b"GGUF","INVALID_GGUF")
    raw.unlink()
    return {"ggufSha256":sha(quantized),"ggufBytes":quantized.stat().st_size,"quantization":"q4_K_M",
            "converterManifestSha256":sha(toolchain_manifest)}


def convert_base(model,toolchain_manifest,output):
    model=safe(model);output=safe(output)
    converted=convert_weights(model,toolchain_manifest,output)
    license_text=(model/"LICENSE").read_text(encoding="utf-8")
    shutil.copyfile(model/"LICENSE",output/"LICENSE")
    (output/"Modelfile").write_bytes(modelfile("./model.gguf",license_text).encode("utf-8"))
    provenance={"schemaVersion":"1.0","baseModelId":"qwen3.5:4b","repository":pins()["repository"],
                "revision":pins()["revision"],"textOnly":True,**converted,"activation":"not-performed"}
    engine=laptop_module();engine.write_json(output/"deployment.json",provenance)
    engine.write_manifest(output,provenance);engine.verify_manifest(output)
    return {"package":str(output),**provenance}


def export_candidate(settings,work):
    engine=laptop_module();approved,base=engine.approval(settings["base_approval"])
    origin=engine.read_json(base/"source.json")
    validate_tools(settings["toolchain_manifest"],origin["toolchainManifestSha256"])
    output=safe(settings["output"]);work=safe(work)
    phase=work/"merge-step";phase.mkdir()
    config=phase/"config.json"
    engine.write_json(config,{key:settings[key] for key in ("base_approval","candidate","output","name")})
    environment=engine.child_environment(os.environ)
    tool_process([sys.executable,"-I","-u","-B","-X","faulthandler",str(Path(__file__).with_name("laptop.py")),"_export",str(config)],environment)
    require((phase/"success.json").is_file(),"QWEN_MERGE_NOT_VERIFIED")
    converted=convert_weights(output/"model",settings["toolchain_manifest"],work/"quantized")
    (work/"quantized/model.gguf").rename(output/"model.gguf")
    finalize_candidate(output,converted)
    return {"package":str(output),"model":settings["name"],**converted,"activation":"not-performed"}


def finalize_candidate(output,converted):
    # This directory is still private staging owned by bounded_child. Rebuild
    # the merge receipt only after adding the verified quantized artifact.
    engine=laptop_module();output=safe(output)
    deployment=engine.read_json(output/"deployment.json")
    deployment.update(converted);deployment.update(repository=pins()["repository"],revision=pins()["revision"],textOnly=True)
    pending=output/"deployment.new.json"
    engine.write_json(pending,deployment);pending.replace(output/"deployment.json")
    (output/"package.json").unlink()
    engine.write_manifest(output,deployment);engine.verify_manifest(output)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-directory",default=str(Path(__file__).resolve().parents[2]/".codex-build/aether-qwen/state"))
    sub=parser.add_subparsers(dest="command",required=True)
    acquire_args=sub.add_parser("acquire");acquire_args.add_argument("--directory",required=True)
    prepare_args=sub.add_parser("prepare");prepare_args.add_argument("--source",required=True);prepare_args.add_argument("--output",required=True);prepare_args.add_argument("--toolchain-manifest",required=True)
    convert_args=sub.add_parser("convert");convert_args.add_argument("--model",required=True);convert_args.add_argument("--output",required=True);convert_args.add_argument("--toolchain-manifest",required=True)
    args=parser.parse_args()
    if args.command=="acquire":result=acquire(args.directory)
    else:
        engine=laptop_module();engine.configure_local(args.state_directory,11435)
        source=args.source if args.command=="prepare" else args.model
        engine.new_output(args.output,[source,args.toolchain_manifest])
        command="_prepare_qwen" if args.command=="prepare" else "_convert_qwen"
        result=engine.bounded_child(command,{"source":str(safe(source)),"output":str(safe(args.output)),
                                    "toolchain_manifest":str(safe(args.toolchain_manifest))},24576 if args.command=="prepare" else 16384,1800)
    print(json.dumps(result,allow_nan=False),flush=True)


if __name__=="__main__":
    try:main()
    except Exception as error:
        import re
        code=str(error) if re.fullmatch("[A-Z0-9_]{1,96}",str(error)) else type(error).__name__.upper()
        print(json.dumps({"error":code}),file=sys.stderr);sys.exit(1)
