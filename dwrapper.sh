#sudo python3 FreqChanger.py 90 & sudo java TraceCollector 90 > $1_$2.csv & ssh hwnam831@ow1 "cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c "$1".json"
./dynamicexp.sh ocr-img $1;./dynamicexp.sh matmul $1;./dynamicexp.sh linpack $1;./dynamicexp.sh primes $1;./dynamicexp.sh ml_training $1;./dynamicexp.sh video_processing $1;./dynamicexp.sh cnn_serving $1;./dynamicexp.sh lr_serving $1;./dynamicexp.sh img-resize $1;./dynamicexp.sh myconfig $1; 