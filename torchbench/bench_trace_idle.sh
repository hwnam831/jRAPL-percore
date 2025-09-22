mkdir -p results
sudo wrmsr --all 0x1a0 0x850089
cd ..
sudo bash reset_freq.sh
sudo docker exec torchbench python /workspace/run_benchmark.py --model yolov3 --precision fp32
sudo bash set_freq.sh $1
sudo java TraceCollector 100 > torchbench/results/idle_$1.csv;
sudo bash reset_freq.sh
