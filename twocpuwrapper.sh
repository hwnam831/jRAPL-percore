#usage twocpuwrapper.sh POLICY PL
ssh hwnam831@ow1 "cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c warmup.json" & sleep 90
FUNCTIONS="base64 image_rotate linpack lr_serving matmul ml_training ocr-img primes rnn_serving video_processing"
for f1 in $FUNCTIONS; do
    for f2 in $FUNCTIONS; do
        if [[ "$f1" != "$f2" ]]; then
            bash twocpuexp.sh $1 $f1 $f2 $2
        fi
    done
done 

