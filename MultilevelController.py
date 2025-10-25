import math
import socket
import time
import threading
import argparse
import signal
import sys
import copy
import random


myPort = 4545
serverRunning = True
lockStatus = threading.Lock()
clients = []
subclusters = []
subclusterLimits = []
powerTargets = {}
nodeStatuses = {}
clusterPowerLimit = 120.0
inc_threshold = 0.9
dec_threshold = 0.8
inc_percentile = 1.1
dec_percentile = 0.9
peak_threshold = 0.5

def signal_handler(sig, frame):
    print('You pressed Ctrl+C!', file=sys.stderr)
    global serverRunning
    serverRunning = False

def ControllerServer(periodms=1000, nsocket=2, subclustersize=2):

    global nodeStatuses
    global clients
    global subclusters
    global subclusterLimits

    #clusterPowerLimit = plimit
    serverSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    serverSocket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    serverSocket.bind(('0.0.0.0', myPort))
    serverSocket.listen(1)
    serverSocket.settimeout(1.0)
    while serverRunning:
        #time.sleep(periodms/1000)
        try:
            (clientSocket, address) = serverSocket.accept()
        except:
            #print("Server timeout. Continue")
            continue
        clientAddress = address[0]
        initialflag = False
        if clientAddress not in clients:
            initialflag = True
            lockStatus.acquire()
            clients.append(clientAddress)
            clients.sort(key=lambda x: int(x.split('.')[-1]))
            subclusters = []
            subclusterLimits = []
            for idx in range(0,len(clients),subclustersize):
                subclusters.append(clients[idx:idx+subclustersize])
                subclusterLimits.append(len(subclusters[-1])*clusterPowerLimit/len(clients))
            
            nodeStatuses[clientAddress] = {}
            socketlimit = clusterPowerLimit/len(clients)/nsocket
            for s in range(nsocket):
                nodeStatuses[clientAddress]['Limit:'+str(s)] = socketlimit
                nodeStatuses[clientAddress]['Consumption:'+str(s)] = socketlimit
                nodeStatuses[clientAddress]['BIPS:'+str(s)] = 0.0
                nodeStatuses[clientAddress]['Util:'+str(s)] = 1.0
                nodeStatuses[clientAddress]['Freq:'+str(s)] = 2.0
                nodeStatuses[clientAddress]['dBIPS/dPower:'+str(s)] = 0.0

                for c in clients:
                    nodeStatuses[c]['Limit:'+str(s)] = socketlimit
            print("New client at: " + str(clientAddress) + " Now total " + str(len(clients)), file=sys.stderr)
            lockStatus.release()

        try:
            data_ = clientSocket.recv(1024)
            dataStr = data_.decode('UTF-8')
            #print("Time = " + str(time.time()) + " From " + str(clientAddress) + " got " + dataStr[2:])
            dataStrList = dataStr[2:].split(',')
            #print(dataStrList)
            #Running average update
            lockStatus.acquire()
            gamma = (200.0) / periodms
            if initialflag:
                for s in range(nsocket):
                    offset = s*5
                    nodeStatuses[clientAddress]['Consumption:'+str(s)] = float(dataStrList[offset+0])
                    nodeStatuses[clientAddress]['BIPS:'+str(s)] = float(dataStrList[offset+1])
                    nodeStatuses[clientAddress]['Util:'+str(s)] = float(dataStrList[offset+2])
                    nodeStatuses[clientAddress]['Freq:'+str(s)] = float(dataStrList[offset+3])
                    nodeStatuses[clientAddress]['dBIPS/dPower:'+str(s)] = float(dataStrList[offset+4])
            else:
                for s in range(nsocket):
                    offset = s*5
                    nodeStatuses[clientAddress]['Consumption:'+str(s)] = \
                        nodeStatuses[clientAddress]['Consumption:'+str(s)] * (1 - gamma) + gamma * float(dataStrList[offset+0])
                    nodeStatuses[clientAddress]['BIPS:'+str(s)] = \
                        nodeStatuses[clientAddress]['BIPS:'+str(s)] * (1 - gamma) + gamma * float(dataStrList[offset+1])
                    nodeStatuses[clientAddress]['Util:'+str(s)] = \
                        nodeStatuses[clientAddress]['Util:'+str(s)] * (1 - gamma) + gamma * float(dataStrList[offset+2])
                    nodeStatuses[clientAddress]['Freq:'+str(s)] = \
                        nodeStatuses[clientAddress]['Freq:'+str(s)] * (1 - gamma) + gamma * float(dataStrList[offset+3])
                    nodeStatuses[clientAddress]['dBIPS/dPower:'+str(s)] = \
                        nodeStatuses[clientAddress]['dBIPS/dPower:'+str(s)] * (1 - gamma) + gamma * float(dataStrList[offset+4])
            lockStatus.release()
            
            
            msg = ",".join([str(nodeStatuses[clientAddress]['Limit:'+str(s)]) for s in range(nsocket)]) + '\n'

            clientSocket.send(msg.encode(encoding="utf-8"))
            clientSocket.close()
        except Exception as e:
            print("Error 1 == "  + str(e), file=sys.stderr)
            pass
    print("server stopped", file=sys.stderr)
    serverSocket.close()

power_max = 200
power_min = 95
grad_max = 5.0
alpha = 0.2
min_freq = 1.0

def printcsv(starttime, NSOC=2):
    csvlines=[str(int((time.time()-starttime)*1000))]
    csvlines += [str(sl) for sl in subclusterLimits]
    for c in clients:
        for s in range(NSOC):
            b2p_grad = nodeStatuses[c]['dBIPS/dPower:'+str(s)]
            csvlines += [str(nodeStatuses[c]['Limit:'+str(s)]),str(nodeStatuses[c]['Consumption:'+str(s)]),
                        str(nodeStatuses[c]['BIPS:'+str(s)]),str(nodeStatuses[c]['Util:'+str(s)]),str(nodeStatuses[c]['Freq:'+str(s)]),str(b2p_grad)]

    print(','.join(csvlines))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-l", "--limit", type=float,
                default='360',help="cluster power limit")
    parser.add_argument("--periodms", type=float,
                default='2000',help="sub-cluster time period in milliseconds")
    parser.add_argument("--centralperiodms", type=float,
                default='8000',help="top-level time period in milliseconds")
    parser.add_argument("--graceperiod", type=float,
                default='10',help="grace period in seconds")
    parser.add_argument("--duration", type=float,
                default='60',help="experiment duration in seconds")
    parser.add_argument("--nsocket", type=int,
                default='1',help="number of cpu sockets per node")
    parser.add_argument("--subclustersize", type=int,
                default='8',help="number of nodes per subclusters")
    parser.add_argument("--lr", type=float,
                default='4',help="learning rate")
    parser.add_argument("--alpha", type=float,
                default='0.2',help="unused power give up rate")
    args=parser.parse_args()
    signal.signal(signal.SIGINT, signal_handler)
    # Set bind address and port

    clusterPowerLimit = args.limit
    NSOC = args.nsocket
    controllerserver = threading.Thread(target=ControllerServer, args=(500,1, args.subclustersize))
    controllerserver.start()

    nextTime = time.time() + args.periodms/1000
    nextCentralTime = time.time() + args.centralperiodms/1000
    counter = 0.0
    deadline = None
    starttime = time.time()
    if args.duration > 0:
        deadline = starttime + args.duration

    default_lr = args.lr
    alpha = args.alpha
    while serverRunning:
        sleeptime = max(nextTime - time.time(), 0.0001)
        time.sleep(sleeptime)
        nextTime = time.time() + args.periodms/1000
        if deadline is not None and time.time() > deadline:
            serverRunning = False
        if len(clients) < 2:
            continue
        if (time.time() - starttime) < args.graceperiod:
            clients.sort(key=lambda x: int(x.split('.')[-1]))
            for c in clients:
                for socket in range(NSOC):
                    nodeStatuses[c]['Limit:'+str(socket)] = clusterPowerLimit/len(clients)/NSOC
            continue
        
        lockStatus.acquire()
        
        b2p_grads = {}
        subclusterConsumptions = []
        subclusterGrads = []
        for idx, subc in enumerate(subclusters):
            totalgrad = 0.0
            totalpower = 1e-6
            subClusterlimit = subclusterLimits[idx]
            for c in subc:
                for s in range(NSOC):
                    totalgrad += nodeStatuses[c]['dBIPS/dPower:' + str(s)]
                    totalpower += nodeStatuses[c]['Consumption:'+str(s)]
                
            for c in subc:
                b2p_grads[c] = [nodeStatuses[c]['dBIPS/dPower:' + str(s)] for s in range(NSOC)]
            subclusterConsumptions.append(totalpower)
            subclusterGrads.append(totalgrad)

            
                
            sum_newpl = 0
            grad_sum=0
            
            for c in subc:
                grad_sum += sum([b2p_grads[c][s] for s in range(NSOC)])
                
            if grad_sum > grad_max * len(subc):
                lr = default_lr * grad_max * len(subc)/grad_sum 
            elif grad_sum < -grad_max:
                lr = -default_lr * grad_max * len(subc)/grad_sum
            else:
                lr = default_lr

            for c in subc:
                for s in range(NSOC):
                    curpl = nodeStatuses[c]['Limit:'+str(s)]
                    newpl = curpl - alpha*(curpl - nodeStatuses[c]['Consumption:'+str(s)]) + lr*b2p_grads[c][s]
                    newpl = max(power_min, newpl)
                    newpl = min(power_max, newpl)
                    sum_newpl += newpl
                    nodeStatuses[c]['Limit:'+str(s)] = newpl

            remainder = 0
            eff_len = len(subc)*NSOC
            coefs = {c:[1 for _ in range(NSOC)] for c in subc}
            if sum_newpl > subClusterlimit:
                delta = (sum_newpl - subClusterlimit)/len(subc)/NSOC
                for c in subc:
                    for s in range(NSOC):
                        newpl = nodeStatuses[c]['Limit:'+str(s)] - delta
                        
                        if nodeStatuses[c]['Freq:'+str(s)] < min_freq:
                            remainder += nodeStatuses[c]['Limit:'+str(s)] + 5 - newpl
                            eff_len = eff_len -1
                            newpl = nodeStatuses[c]['Limit:'+str(s)] + 5
                            coefs[c][s] = 0
                        else:
                            coefs[c][s] = 1
                        nodeStatuses[c]['Limit:'+str(s)] = newpl
                    
                for c in subc:
                    if eff_len <= 0.1:
                        break
                    for s in range(NSOC):
                        nodeStatuses[c]['Limit:'+str(s)] -= coefs[c][s]*remainder/eff_len
        if time.time() > nextCentralTime:
            nextCentralTime = time.time() + args.centralperiodms/1000
            sum_newpl = 0
            lr = default_lr / len(subclusters)
            for idx in range(len(subclusters)):
                curpl = subclusterLimits[idx]
                newpl = curpl - alpha*(curpl - subclusterConsumptions[idx])/ len(subclusters) + lr*subclusterGrads[idx]
                newpl = max(power_min*len(subclusters[idx]), newpl)
                newpl = min(power_max*len(subclusters[idx]), newpl)
                sum_newpl += newpl
                subclusterLimits[idx] = newpl
                pldiff = newpl - curpl
                for c in subclusters[idx]:
                    for s in range(NSOC):
                        nodeStatuses[c]['Limit:'+str(s)] = nodeStatuses[c]['Limit:'+str(s)] + pldiff/len(subclusters[idx])/NSOC

            delta = (sum_newpl - clusterPowerLimit)/len(subclusters)
            for idx in range(len(subclusters)):
                subclusterLimits[idx] = subclusterLimits[idx] - delta
                for c in subclusters[idx]:
                    for s in range(NSOC):
                        nodeStatuses[c]['Limit:'+str(s)] = nodeStatuses[c]['Limit:'+str(s)] - delta/len(subclusters[idx])/NSOC

        
        lockStatus.release()
        printcsv(starttime, NSOC=NSOC)
    print("controller stopped", file=sys.stderr)
    clientcount = 0
    headerstr = ['Time(ms)']
    headerstr += ['SubclusterLimit:' + str(idx) for idx in range(len(subclusters))]
    for c in clients:
        clientcount += 1
        for s in range(NSOC):
            headerstr += [f"Limit:{clientcount}:{s}",f"Consumption:{clientcount}:{s}",
                        f"BIPS:{clientcount}:{s}",f"Util:{clientcount}:{s}",
                        f"Freq:{clientcount}:{s}",f"Grad:{clientcount}:{s}"]
    print(','.join(headerstr))
    print(clients, file=sys.stderr)
    controllerserver.join()
    #TODO: test sinusoidal
    # Create a socket for receiving connections
    
