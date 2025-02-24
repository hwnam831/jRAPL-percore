#Usage: hierarchicalexp.sh [limit] [duration]
TOTALCAP=$(($2*4))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$2" "$1" stable-diffusion stable-diffusion" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$2" "$1" cnn-serving cnn-serving " &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$2" "$1" llama-3.1-8b llama-3.1-8b" &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$2" "$1" vits-ljs vits-ljs " &
python3 ClusterController.py --policy hierarchical --limit $TOTALCAP --duration $2 > hierarchical_ml_$1.csv;