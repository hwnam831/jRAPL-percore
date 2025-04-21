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
pernodelimit = 60.0
inc_threshold = 0.9
dec_threshold = 0.8
inc_percentile = 1.1
dec_percentile = 0.9
peak_threshold = 0.5

def signal_handler(sig, frame):
    print('You pressed Ctrl+C!', file=sys.stderr)
    global serverRunning
    serverRunning = False

def ControllerServer(periodms=1000):

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
            
            nodeStatuses[clientAddress] = {
                'Limit:0' : pernodelimit/2,
                'Consumption:0' : pernodelimit/2,
                'BIPS:0' : 0.0,
                'Util:0' : 1.0,
                'Freq:0' : 2.0,
                'dBIPS/dPower:0' : 0.0,
                'Limit:1' : pernodelimit/2,
                'Consumption:1' : pernodelimit/2,
                'BIPS:1' : 0.0,
                'Util:1' : 1.0,
                'Freq:0' : 2.0,
                'dBIPS/dPower:1' : 0.0,
            }
            for c in clients:
                nodeStatuses[c]['Limit:0'] = pernodelimit/2
                nodeStatuses[c]['Limit:1'] = pernodelimit/2
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
                nodeStatuses[clientAddress]['Consumption:0'] = float(dataStrList[0])
                nodeStatuses[clientAddress]['BIPS:0'] = float(dataStrList[1])
                nodeStatuses[clientAddress]['Util:0'] = float(dataStrList[2])
                nodeStatuses[clientAddress]['Freq:0'] = float(dataStrList[3])
                nodeStatuses[clientAddress]['dBIPS/dPower:0'] = float(dataStrList[4])

                nodeStatuses[clientAddress]['Consumption:1'] = float(dataStrList[5])
                nodeStatuses[clientAddress]['BIPS:1'] = float(dataStrList[6])
                nodeStatuses[clientAddress]['Util:1'] = float(dataStrList[7])
                nodeStatuses[clientAddress]['Freq:1'] = float(dataStrList[8])
                nodeStatuses[clientAddress]['dBIPS/dPower:1'] = float(dataStrList[9])
            else:
                nodeStatuses[clientAddress]['Consumption:0'] = \
                    nodeStatuses[clientAddress]['Consumption:0'] * (1 - gamma) + gamma * float(dataStrList[0])
                nodeStatuses[clientAddress]['BIPS:0'] = \
                    nodeStatuses[clientAddress]['BIPS:0'] * (1 - gamma) + gamma * float(dataStrList[1])
                nodeStatuses[clientAddress]['Util:0'] = \
                    nodeStatuses[clientAddress]['Util:0'] * (1 - gamma) + gamma * float(dataStrList[2])
                nodeStatuses[clientAddress]['Freq:0'] = \
                    nodeStatuses[clientAddress]['Freq:0'] * (1 - gamma) + gamma * float(dataStrList[3])
                nodeStatuses[clientAddress]['dBIPS/dPower:0'] = \
                    nodeStatuses[clientAddress]['dBIPS/dPower:0'] * (1 - gamma) + gamma * float(dataStrList[4])

                nodeStatuses[clientAddress]['Consumption:1'] = \
                    nodeStatuses[clientAddress]['Consumption:1'] * (1 - gamma) + gamma * float(dataStrList[5])
                nodeStatuses[clientAddress]['BIPS:1'] = \
                    nodeStatuses[clientAddress]['BIPS:1'] * (1 - gamma) + gamma * float(dataStrList[6])
                nodeStatuses[clientAddress]['Util:1'] = \
                    nodeStatuses[clientAddress]['Util:1'] * (1 - gamma) + gamma * float(dataStrList[7])
                nodeStatuses[clientAddress]['Freq:1'] = \
                    nodeStatuses[clientAddress]['Freq:1'] * (1 - gamma) + gamma * float(dataStrList[8])
                nodeStatuses[clientAddress]['dBIPS/dPower:1'] = \
                    nodeStatuses[clientAddress]['dBIPS/dPower:1'] * (1 - gamma) + gamma * float(dataStrList[9])
            lockStatus.release()
            
            
            msg = str(nodeStatuses[clientAddress]['Limit:0'])+"," +\
                  str(nodeStatuses[clientAddress]['Limit:1'])+"\n"
            clientSocket.send(msg.encode(encoding="utf-8"))
            clientSocket.close()
        except Exception as e:
            print("Error 1 == "  + str(e), file=sys.stderr)
            pass
    print("server stopped", file=sys.stderr)
    serverSocket.close()

power_max = 105
power_min = 20
grad_max = 5.0
alpha = 0.2
default_lr = 2.0
min_freq = 1.2

def printcsv(starttime):
    csvlines=[str(int((time.time()-starttime)*1000))]
    totalbips = 0
    totalpower = 0
    for c in clients:
        totalbips += nodeStatuses[c]['BIPS:0']
        totalbips += nodeStatuses[c]['BIPS:1']
        totalpower += nodeStatuses[c]['Consumption:0']
        totalpower += nodeStatuses[c]['Consumption:1']
    for c in clients:
        #b2p_grad = 2*(totalbips/totalpower)*nodeStatuses[c]['dBIPS/dPower:0'] - (totalbips/totalpower)*(totalbips/totalpower)
        b2p_grad = nodeStatuses[c]['dBIPS/dPower:0']
        csvlines += [str(nodeStatuses[c]['Limit:0']),str(nodeStatuses[c]['Consumption:0']),
                     str(nodeStatuses[c]['BIPS:0']),str(nodeStatuses[c]['Util:0']),str(nodeStatuses[c]['Freq:0']),str(b2p_grad)]
        #b2p_grad = 2*(totalbips/totalpower)*nodeStatuses[c]['dBIPS/dPower:1'] - (totalbips/totalpower)*(totalbips/totalpower)
        b2p_grad = nodeStatuses[c]['dBIPS/dPower:1']
        csvlines += [str(nodeStatuses[c]['Limit:1']),str(nodeStatuses[c]['Consumption:1']),
                     str(nodeStatuses[c]['BIPS:1']),str(nodeStatuses[c]['Util:1']),str(nodeStatuses[c]['Freq:1']),str(b2p_grad)]
    print(','.join(csvlines))

# 20 Tokens total
# 12 tokens by default
def requiredTokens(util, prevutil, bips, prevbips, token, prevtoken):
    if token - prevtoken == 1:
        if bips > prevbips:
            return int(util * 20)
        else:
            return prevtoken
    elif prevtoken - token == 1:
        if bips < prevbips:
            return int(util * 20)
        else:
            return prevtoken
    elif util > prevutil:
        return token + 1
    elif util < prevutil:
        return token - 1
    else:
        return int(util * 20)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--policy", type=str,
                        choices=['slurm','ml','dps','fair','tokensmart','hierarchical','geoml'],
                default='fair',help="policy")
    parser.add_argument("-l", "--limit", type=float,
                default='60',help="per-node power limit")
    parser.add_argument("--periodms", type=float,
                default='2000',help="time period in milliseconds")
    parser.add_argument("--groupperiod", type=float,
                default='8',help="cluster time period in seconds")
    parser.add_argument("--graceperiod", type=float,
                default='10',help="grace period in seconds")
    parser.add_argument("--duration", type=float,
                default='-1',help="experiment duration in seconds")
    args=parser.parse_args()
    signal.signal(signal.SIGINT, signal_handler)
    # Set bind address and port

    pernodelimit = args.limit
    controllerserver = threading.Thread(target=ControllerServer)
    controllerserver.start()

    nextTime = time.time() + args.periodms/1000
    nextGroup = time.time() + args.groupperiod
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
    tokenpool = 0
    starvationThreshold = 32


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
                nodeStatuses[c]['Limit:0'] = pernodelimit/2
                nodeStatuses[c]['Limit:1'] = pernodelimit/2
            continue
        lockStatus.acquire()
        totalbips = 0.0
        totalpower = 1e-6
        b2p_grads = {}
        
        for c in clients:
            totalbips += nodeStatuses[c]['BIPS:0']
            totalbips += nodeStatuses[c]['BIPS:1']
            totalpower += nodeStatuses[c]['Consumption:0']
            totalpower += nodeStatuses[c]['Consumption:1']
            if not c in tokens:
                tokens[c] = [12,12]
                prevtokens[c] = [12,12]
                prevutils[c] = [nodeStatuses[c]['Util:0'],nodeStatuses[c]['Util:1']]
                prevbips[c] = [nodeStatuses[c]['BIPS:0'],nodeStatuses[c]['BIPS:1']]
                prevpower[c] = [nodeStatuses[c]['Consumption:0'],nodeStatuses[c]['Consumption:0']]
                peakratio[c] = [peak_threshold,peak_threshold]
            
        for c in clients:
            b2p_grads[c] = [nodeStatuses[c]['dBIPS/dPower:0'],nodeStatuses[c]['dBIPS/dPower:1']]
            
            
        lr = default_lr
        groups = []
        group_limits = []
        group_grads = []
        group_pows = []
        for i in range(0,len(clients),4):
            groups.append(clients[i:i+4])
        
        for group in groups:
            group_limit = 0
            group_grad = 0
            group_pow = 0
            for c in group:
                group_limit += nodeStatuses[c]['Limit:0'] + nodeStatuses[c]['Limit:1']
                group_grad += (b2p_grads[c][0] + b2p_grads[c][1])/(2*len(group))
                group_pow += nodeStatuses[c]['Consumption:0'] + nodeStatuses[c]['Consumption:1']
            group_limits.append(group_limit)
            group_grads.append(group_grad)
            group_pows.append(group_pow)
            totalpower += group_pow
        clusterLimit = pernodelimit*len(clients)

        # Infrequent group update
        sum_newlimit = 0
        if time.time() > nextGroup:
            nextGroup = time.time() + args.groupperiod

            for i in range(len(groups)):
                group = groups[i]
                group_limit = group_limits[i]
                group_grad = group_grads[i]
                group_pow = group_pows[i]
                newlimit = group_limit - alpha*(group_limit - group_pow) + lr*group_grad
                newlimit = max(power_min*2*len(group), newlimit)
                newlimit = min(power_max*2*len(group), newlimit)
                sum_newlimit += newlimit
            groupdelta = (sum_newlimit - clusterLimit)/len(groups)
            for i in range(len(groups)):
                group_limits[i] = group_limits[i] - groupdelta
            
        for i,group in enumerate(groups):
            sum_newpl = 0
            group_limit = group_limits[i]
            for c in group:
                curpl = nodeStatuses[c]['Limit:0'] + nodeStatuses[c]['Limit:1']
                curusage = nodeStatuses[c]['Consumption:0'] + nodeStatuses[c]['Consumption:1']
                nodegrad =(b2p_grads[c][0] + b2p_grads[c][1])*0.5
                newpl = curpl - alpha*(curpl - curusage) + lr*nodegrad*0.5
                newpl = max(power_min*2, newpl)
                newpl = min(power_max*2, newpl)
                sum_newpl += newpl
                nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] + (newpl-curpl)*0.5
                nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] + (newpl-curpl)*0.5
            
            delta = (sum_newpl - group_limit)/len(group)/2
            for c in group:
                nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] - delta
                nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] - delta
            
        
        lockStatus.release()
        printcsv(starttime)
    print("controller stopped", file=sys.stderr)
    clientcount = 0
    headerstr = ['Time(ms)']
    for c in clients:
        clientcount += 1
        headerstr += ['Limit:' + str(clientcount) + ":0",'Consumption:' + str(clientcount) + ":0",
                      'BIPS:' + str(clientcount) + ":0",'Util:' + str(clientcount) + ":0",
                      'Freq:' + str(clientcount) + ":0",'Grad:' + str(clientcount) + ":0"]
        headerstr += ['Limit:' + str(clientcount) + ":1",'Consumption:' + str(clientcount) + ":1",
                      'BIPS:' + str(clientcount) + ":1",'Util:' + str(clientcount) + ":1",
                      'Freq:' + str(clientcount) + ":1",'Grad:' + str(clientcount) + ":1"]
    print(','.join(headerstr))
    print(clients, file=sys.stderr)
    controllerserver.join()
    #TODO: test sinusoidal
    # Create a socket for receiving connections
    
