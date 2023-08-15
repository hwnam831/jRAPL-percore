#usage twocpuwrapper.sh POLICY PL
ssh hwnam831@ow1 "cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c warmup.json" & sleep 90
FUNCTIONS="base64 image_rotate linpack lr_serving matmul ml_training ocr-img primes rnn_serving video_processing"

bash twocpuexp.sh $1 matmul image_rotate $2
bash twocpuexp.sh $1 ocr-img video_processing $2
bash twocpuexp.sh $1 base64 primes $2
bash twocpuexp.sh $1 lr_serving rnn_serving $2
bash twocpuexp.sh $1 linpack ml_training $2
bash twocpuexp.sh $1 rnn_serving ocr-img $2
bash twocpuexp.sh $1 video_processing linpack $2
bash twocpuexp.sh $1 primes matmul $2
bash twocpuexp.sh $1 ml_training lr_serving $2
bash twocpuexp.sh $1 image_rotate base64 $2

