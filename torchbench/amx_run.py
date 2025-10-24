import sys
sys.path.insert(0,'/benchmark/')
import time
import random
import argparse
import os
import math
import torchbenchmark.models.yolov3
import torch
import numpy as np

def PoissonGen(rate, interval, seed=1):
    n = int(rate*interval)
    random.seed(seed)
    arr = [-np.log(random.random())/rate for _ in range(2*n)]
    acc = 0
    times = []
    for t in arr:
        acc += t
        if acc > interval:
            break
        times.append(acc)
    return times

if __name__=='__main__':
    
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--workload",
        type=str,
        default="low",
        choices=['low','med','high','random','sinusoidal'],
        help="workload heaviness",
    )
    parser.add_argument(
        "--duration", type=int, default=60, help="Benchmark duration in seconds"
    )
    parser.add_argument(
        "--downloadonly",
        action="store_true"
    )
    parser.add_argument(
        "--idle", type=float, default=0.3, help="idle percentage"
    )
    parser.add_argument(
        "--tag", type=str, default='default', help="exp tag"
    )
    parser.add_argument(
        "--continuous",
        action="store_true"
    )
    parser.add_argument(
        "--config", type=int, default=1, help="Arrival trace choice"
    )
    args = parser.parse_args()
    random.seed(args.config)
    
    
    #img = img.expand_dims(axis=0) # batchify
    begintime = time.time()
    curtime = begintime
    endtime = curtime + args.duration
    csvlines = []
    csvlines.append("Curtime,Elapsed,Batchsize")
    if args.workload == 'low':
        arrivals = PoissonGen(1.5, args.duration, args.config)
        bsize = 16
    elif args.workload == 'med':
        arrivals = PoissonGen(1.5, args.duration, args.config)
        bsize = 8
    elif args.workload == 'high':
        arrivals = PoissonGen(0.4, args.duration, args.config)
        bsize = 64
    else: # high
        arrivals = PoissonGen(0.4, args.duration, args.config)
        bsize = 64
        
    model, example_inputs = torchbenchmark.models.yolov3.Model(test="eval", device="cpu", batch_size=bsize).get_module()
    with torch.autocast(device_type="cpu", dtype=torch.bfloat16):
        model(*example_inputs)
    if (not args.downloadonly):
        logsum = 0
        total = 0
        count = 0
        if args.continuous:
            while curtime < endtime:
                
                with torch.autocast(device_type="cpu", dtype=torch.bfloat16):
                    out = model(*example_inputs)
                elapsed = time.time() - curtime
                logsum += math.log(elapsed)
                total += elapsed
                count += 1
                csvlines.append(f"{curtime-begintime},{elapsed}")
                curtime = time.time()
        else:
            for t in arrivals:
                curtime = time.time() - begintime
                if t > args.duration:
                    break
                if curtime < t:
                    time.sleep(t-curtime)
                with torch.autocast(device_type="cpu", dtype=torch.bfloat16):
                    out = model(*example_inputs)
                elapsed = time.time() - t - begintime
                logsum += math.log(elapsed)
                total += elapsed
                count += 1
                csvlines.append(f"{curtime},{elapsed},{bsize}")
        gmean = math.exp(logsum/count)
        #csvlines.append(f"Geometric Mean,{gmean}")
        csvlines.append(f"Average,{total/count},{bsize}")
    with open(f"/mydata/workspace/jrapl/{args.tag}_yolov3_{args.workload}.csv", "w") as f:
        f.write("\n".join(csvlines))
    # format image as (batch, RGB, width, height)