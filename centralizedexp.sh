#Usage: clusterexp.sh [policy] [limit] [duration]
PERNODECAP=$(($2/2))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$PERNODECAP" stable-diffusion llama-3.1-8b" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$PERNODECAP" vits-ljs cnn-serving " &
python3 ClusterController.py --policy $1 --limit $2 --duration $3 > centralized_$1_central_$2.csv