#Usage: clusterexp.sh [policy] [limit]
PERNODECAP=$(($2/4))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
python3 ClusterController.py --policy $1 --limit $2 --duration 600 > centralized_$1_central_$2.csv &
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh 600 "$PERNODECAP" vits-ljs stable-diffusion " &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh 600 "$PERNODECAP" llama-3.1-8b cnn-serving " &