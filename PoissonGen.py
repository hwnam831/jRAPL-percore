import numpy as np
import random
import sys

def PoissonGen(rate, interval):
    n = int(rate*interval)
    arr = [-np.log(random.random())/rate for _ in range(2*n)]
    acc = 0
    times = []
    for t in arr:
        acc += t
        if acc > interval:
            break
        times.append(acc)
    return times

if __name__ == "__main__":
    rate = float(sys.argv[1])
    interval = float(sys.argv[2])
    arr = PoissonGen(rate, interval)
    while len(arr) < int(rate*interval) - 1 or len(arr) > int(rate*interval) + 1:
        arr = PoissonGen(rate, interval)
    arrstr = ",".join([" {:.4f}".format(t) for t in arr])
    print(arrstr)