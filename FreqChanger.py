import json
import os
import requests
import subprocess
import sys
import time
import threading
import logging
import random

maxfreq=2600
minfreq=1200
steps=[-300,-200,-100,100,200,300]
num_cores = 20

def set_freq(freq):
    os.system("cpupower frequency-set -f {}MHz >/dev/null 2>&1".format(freq))

if __name__=='__main__':
    starttime=time.time()
    curfreq = maxfreq
    if len(sys.argv) < 2:
        duration=30
    else:
        duration = int(sys.argv[1])
    #print(duration)
    epochs = (duration*1000)//20
    count = {f:0 for f in range(minfreq,maxfreq+1,100)}
    for e in range(epochs):
        if curfreq == maxfreq:
            curfreq = curfreq + steps[random.randint(0,2)]
        elif curfreq == minfreq:
            curfreq = curfreq + steps[random.randint(3,5)]
        else:
            curfreq = curfreq + steps[random.randint(0,5)]
        curfreq = min(curfreq,maxfreq)
        curfreq = max(curfreq,minfreq)
        count[curfreq] = count[curfreq]+1
        set_freq(curfreq)
        time.sleep(20/1000)
    set_freq(maxfreq)
    print(count)
    #print(time.time() - starttime)
        