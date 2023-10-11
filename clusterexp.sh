#Usage: clusterexp.sh [policy] [limit]
PERNODECAP=$(($2/2))
python3 ClusterController.py --policy $1 --limit $2 --duration 150 > clusterexp1_$1_$2.csv&
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$1" 90 "$PERNODECAP" clusterexp1"$1"" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$1" 90 "$PERNODECAP" clusterexp1"$1"" &
cd /mydata/workspace/faas-profiler; ./WorkloadInvoker -c clusterexp1.json &
sleep 180