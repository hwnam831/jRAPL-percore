#Usage: hierarchicalexp.sh [policy] [limit] [duration]
TOTALCAP=$(($2*4))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$3" "$2" llama-3.1-8b stable-diffusion high high" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$3" "$2" stable-diffusion cnn-serving low low" &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$3" "$2" cnn-serving vits-ljs high high" &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$3" "$2" vits-ljs llama-3.1-8b low low" &
python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $3 > results/hierarchical3_$1_$2.csv;