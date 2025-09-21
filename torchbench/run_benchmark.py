import os
import argparse
import subprocess

configs = {
    "densenet121": {
        "batch_sizes": [16, 32, 64, 128],
        "total_samples": 1600,
        "test": "eval",
    },
    "dcgan": {
        "batch_sizes": [16, 32, 64, 128],
        "total_samples": 3200,
        "test": "train",
    },
    "BERT_pytorch": {
        "batch_sizes": [8, 16, 32, 64],
        "total_samples": 1280,
        "test": "eval",
    },
    "dlrm": {
        "batch_sizes": [64, 128, 256, 512],
        "total_samples": 128000,
        "test": "train",

    },
    "timm_vision_transformer": {
        "batch_sizes": [16, 32, 64, 128],
        "total_samples": 3200,
        "test": "eval",

    },
    "yolov3": {
        "batch_sizes": [4, 8, 16, 32],
        "total_samples": 400,
        "test": "eval",
    },
}


if __name__ == "__main__":
    os.chdir('/benchmark')
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=str, required=True, choices=configs.keys())
    parser.add_argument("--precision", type=str, default="fp32", choices=["fp32", "amp_bf16"])
    args = parser.parse_args()
    expconfig = configs[args.model]
    precision = args.precision if expconfig["test"] == "eval" else "fp32"
    

    for batch_size in expconfig["batch_sizes"]:
        niter = expconfig["total_samples"] // batch_size
        bsize = 2*batch_size if precision == "amp_bf16" else batch_size
        cmd = f"python run_benchmark.py cpu --model {args.model} -b {bsize} -t {expconfig['test']} --precision {precision} --niter {niter}"
        os.system(cmd)

