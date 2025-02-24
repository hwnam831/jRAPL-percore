#Usage: clusterexp.sh [policy] [limit] [duration]
TOTALCAP=$(($2*4))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" stable-diffusion stable-diffusion" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" cnn-serving cnn-serving " &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" llama-3.1-8b llama-3.1-8b" &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_centralized.sh "$3" "$2" vits-ljs vits-ljs " &
python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $3 > centralized_$1_central_$2.csv;