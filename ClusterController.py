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

def ControllerServer(periodms=1000, nsocket=2):

    global nodeStatuses
    global clients

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
power_min = 100
grad_max = 5.0
alpha = 0.1
min_freq = 0.8

def printcsv(starttime, NSOC=2):
    csvlines=[str(int((time.time()-starttime)*1000))]

    for c in clients:
        for s in range(NSOC):
            b2p_grad = nodeStatuses[c]['dBIPS/dPower:'+str(s)]
            csvlines += [str(nodeStatuses[c]['Limit:'+str(s)]),str(nodeStatuses[c]['Consumption:'+str(s)]),
                        str(nodeStatuses[c]['BIPS:'+str(s)]),str(nodeStatuses[c]['Util:'+str(s)]),str(nodeStatuses[c]['Freq:'+str(s)]),str(b2p_grad)]

    print(','.join(csvlines))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--policy", type=str,
                        choices=['slurm','ml','dps','fair','hierarchical','geoml'],
                default='fair',help="policy")
    parser.add_argument("-l", "--limit", type=float,
                default='360',help="cluster power limit")
    parser.add_argument("--periodms", type=float,
                default='8000',help="time period in milliseconds")
    parser.add_argument("--graceperiod", type=float,
                default='10',help="grace period in seconds")
    parser.add_argument("--duration", type=float,
                default='60',help="experiment duration in seconds")
    parser.add_argument("--nsocket", type=int,
                default='1',help="number of cpu sockets per node")
    parser.add_argument("--lr", type=float,
                default='4',help="learning rate")
    parser.add_argument("--alpha", type=float,
                default='0.2',help="unused power give up rate")
    args=parser.parse_args()
    signal.signal(signal.SIGINT, signal_handler)
    # Set bind address and port

    clusterPowerLimit = args.limit
    NSOC = args.nsocket
    controllerserver = threading.Thread(target=ControllerServer, args=(1000,1))
    controllerserver.start()

    nextTime = time.time() + args.periodms/1000
    counter = 0.0
    deadline = None
    starttime = time.time()
    if args.duration > 0:
        deadline = starttime + args.duration
    tokens = {}
    prevtokens = {}

    prevutils = {}

    prevbips = {}
    prevpower = {}
    peakratio = {}
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
        totalbips = 0.0
        totalpower = 1e-6
        b2p_grads = {}
        
        for c in clients:
            for socket in range(NSOC):
                totalbips += nodeStatuses[c]['BIPS:'+str(socket)]
                totalpower += nodeStatuses[c]['Consumption:'+str(socket)]
            if not c in tokens:
                tokens[c] = [12,12]
                prevtokens[c] = [12,12]
                prevutils[c] = [nodeStatuses[c]['Util:' + str(s)] for s in range(NSOC)]
                prevbips[c] = [nodeStatuses[c]['BIPS:' + str(s)] for s in range(NSOC)]
                prevpower[c] = [nodeStatuses[c]['Consumption:' + str(s)] for s in range(NSOC)]
                peakratio[c] = [peak_threshold for s in range(NSOC)]
            
        for c in clients:
            b2p_grads[c] = [nodeStatuses[c]['dBIPS/dPower:' + str(s)] for s in range(NSOC)]
            for socket in range(NSOC):
                peakratio[c][socket] = peakratio[c][socket] * 0.9
                initial_cap = clusterPowerLimit/len(clients)/NSOC
                if (nodeStatuses[c]['Consumption:'+str(socket)] > initial_cap * inc_threshold):
                    peakratio[c][socket] += 0.1


        if args.policy == "hierarchical":
            sum_newpl = 0
            grad_sum=0
                
            for c in clients:
                grad_sum += b2p_grads[c][0] + b2p_grads[c][1]
                
            if grad_sum > grad_max * len(clients):
                lr = default_lr * grad_max * len(clients)/grad_sum 
            elif grad_sum < -grad_max:
                lr = -default_lr * grad_max * len(clients)/grad_sum
            else:
                lr = default_lr


            for c in clients:
                curpl = nodeStatuses[c]['Limit:0'] + nodeStatuses[c]['Limit:1']
                curusage = nodeStatuses[c]['Consumption:0'] + nodeStatuses[c]['Consumption:1']
                nodegrad =(b2p_grads[c][0] + b2p_grads[c][1])*0.5
                newpl = curpl - alpha*(curpl - curusage) + lr*nodegrad
                newpl = max(power_min*2, newpl)
                newpl = min(power_max*2, newpl)
                sum_newpl += newpl
                nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] + (newpl-curpl)*0.5
                nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] + (newpl-curpl)*0.5

            remainder = 0
            eff_len = len(clients)
            coefs = {}
            if sum_newpl > clusterPowerLimit:
                delta = (sum_newpl - clusterPowerLimit)/len(clients)/2
                for c in clients:
                    nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] - delta
                    nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] - delta
            else:
                excess = (clusterPowerLimit - sum_newpl)/len(clients)/2
                for c in clients:
                    nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] + excess
                    nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] + excess
        elif args.policy == "ml":
            
            sum_newpl = 0
            grad_sum=0
            
            for c in clients:
                grad_sum += sum([b2p_grads[c][s] for s in range(NSOC)])
                
            if grad_sum > grad_max * len(clients):
                lr = default_lr * grad_max * len(clients)/grad_sum 
            elif grad_sum < -grad_max:
                lr = -default_lr * grad_max * len(clients)/grad_sum
            else:
                lr = default_lr

            for c in clients:
                for s in range(NSOC):
                    curpl = nodeStatuses[c]['Limit:'+str(s)]
                    newpl = curpl - alpha*(curpl - nodeStatuses[c]['Consumption:'+str(s)]) + lr*b2p_grads[c][s]
                    newpl = max(power_min, newpl)
                    newpl = min(power_max, newpl)
                    sum_newpl += newpl
                    nodeStatuses[c]['Limit:'+str(s)] = newpl

            remainder = 0
            eff_len = len(clients)*NSOC
            coefs = {c:[1 for _ in range(NSOC)] for c in clients}
            if sum_newpl > clusterPowerLimit:
                delta = (sum_newpl - clusterPowerLimit)/len(clients)/NSOC
                for c in clients:
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
                    
                for c in clients:
                    if eff_len <= 0.1:
                        break
                    for s in range(NSOC):
                        nodeStatuses[c]['Limit:'+str(s)] -= coefs[c][s]*remainder/eff_len

        elif args.policy == 'slurm':
            pool = 0.0
            beta = len(clients) / (len(clients) - 0.99)
            for c in clients:
                for s in range(NSOC):
                    diff = nodeStatuses[c]['Limit:'+str(s)]-nodeStatuses[c]['Consumption:'+str(s)]
                    if diff>0.0:
                        pool += 0.5*diff* beta
                        nodeStatuses[c]['Limit:'+str(s)] = nodeStatuses[c]['Limit:'+str(s)] - 0.5*diff* beta

            for c in clients:
                for s in range(NSOC):
                    nodeStatuses[c]['Limit:'+str(s)] = nodeStatuses[c]['Limit:'+str(s)] + pool/len(clients)/NSOC

        elif args.policy == 'fair':
            for c in clients:
                for s in range(NSOC):
                    nodeStatuses[c]['Limit:'+str(s)] = clusterPowerLimit/len(clients)/NSOC
        elif args.policy == 'dps':
            # Restore unit
            initial_cap = clusterPowerLimit/len(clients)/NSOC
            restore_flag = True
            for c in clients:
                for s in range(NSOC):
                    restore_flag = restore_flag and \
                        nodeStatuses[c]['Consumption:'+str(s)] <= initial_cap * inc_threshold
                if not restore_flag:
                    break

            # Stateless unit

            totalcap = 0
            for c in clients:
                for s in range(NSOC):
                    if (nodeStatuses[c]['Consumption:'+str(s)] < nodeStatuses[c]['Limit:'+str(s)] * dec_threshold):
                        nodeStatuses[c]['Limit:'+str(s)] = nodeStatuses[c]['Limit:'+str(s)] * dec_percentile
                    totalcap += nodeStatuses[c]['Limit:'+str(s)]
            avail_budget = clusterPowerLimit - totalcap
            idxlist = list(range(len(clients)))
            random.shuffle(idxlist)
            for idx in idxlist:
                c = clients[idx]
                for s in range(NSOC):
                    if (nodeStatuses[c]['Consumption:'+str(s)] > nodeStatuses[c]['Limit:'+str(s)] * inc_threshold):
                        tempt = min(avail_budget, nodeStatuses[c]['Limit:'+str(s)] * (inc_percentile - 1.0))
                        nodeStatuses[c]['Limit:'+str(s)] = nodeStatuses[c]['Limit:'+str(s)] + tempt
                        avail_budget -= tempt

            # Priority module
            priority_flags = {c:[False for s in range(NSOC)] for c in clients}
            for c in clients:
                for s in range(NSOC):
                    if peakratio[c][s] > peak_threshold:
                        priority_flags[c][s] = True
                    direv = nodeStatuses[c]['Consumption:'+str(s)] - prevpower[c][s]
                    prevpower[c][s] = nodeStatuses[c]['Consumption:'+str(s)]
                    if direv > prevpower[c][s] * (inc_percentile - 1.0):
                        priority_flags[c][s] = True
            
            # Readjusting module
            budget_high = 0.0
            count_high = 1e-6
            for c in clients:
                for s in range(NSOC):
                    if priority_flags[c][s]:
                        budget_high += nodeStatuses[c]['Limit:'+str(s)]
                        count_high += 1

            if avail_budget > 0:
                total = 0.0
                for c in clients:
                    for s in range(NSOC):
                        if priority_flags[c][s]:
                            total += budget_high/nodeStatuses[c]['Limit:'+str(s)]

                for c in clients:
                    for s in range(NSOC):
                        if priority_flags[c][s]:
                            nodeStatuses[c]['Limit:'+str(s)] += avail_budget * budget_high/nodeStatuses[c]['Limit:'+str(s)]/total
                            nodeStatuses[c]['Limit:'+str(s)] = min(nodeStatuses[c]['Limit:'+str(s)], power_max)

            else:
                readjusted_cap = budget_high/count_high
                for c in clients:
                    for s in range(NSOC):
                        if priority_flags[c][s]:
                            nodeStatuses[c]['Limit:'+str(s)] = readjusted_cap
            
            if restore_flag:
                for c in clients:
                    for s in range(NSOC):
                        nodeStatuses[c]['Limit:'+str(s)] = initial_cap

        else:
            pass
        lockStatus.release()
        printcsv(starttime, NSOC=NSOC)
    print("controller stopped", file=sys.stderr)
    clientcount = 0
    headerstr = ['Time(ms)']
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
    
