mkdir -p results
sudo wrmsr --all 0x1a0 0x850089
cd ..
sudo docker exec torchbench python /workspace/run_benchmark.py --model $1 --precision fp32 &
sudo java TraceCollector 300 > torchbench/results/$1_fp32_turbo.csv;
sudo docker exec torchbench python /workspace/run_benchmark.py --model $1 --precision amp_bf16 &
sudo java TraceCollector 300 > torchbench/results/$1_bf16_turbo.csv;
