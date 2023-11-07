#Usage: clusterexp.sh [policy] [limit] [config]
PERNODECAP=$(($2/3))
python3 ClusterController.py --policy $1 --limit $2 --duration 130 > $3_$1_$2.csv&
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$1" 120 "$PERNODECAP" "$3"_"$1"" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$1" 120 "$PERNODECAP" "$3"_"$1"" &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$1" 120 "$PERNODECAP" "$3"_"$1"" &
cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c $3.json &
sleep 180