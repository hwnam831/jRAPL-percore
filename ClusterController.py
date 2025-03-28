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
                'Limit:0' : clusterPowerLimit/len(clients)/2,
                'Consumption:0' : clusterPowerLimit/len(clients)/2,
                'BIPS:0' : 0.0,
                'Util:0' : 1.0,
                'Freq:0' : 2.0,
                'dBIPS/dPower:0' : 0.0,
                'Limit:1' : clusterPowerLimit/len(clients)/2,
                'Consumption:1' : clusterPowerLimit/len(clients)/2,
                'BIPS:1' : 0.0,
                'Util:1' : 1.0,
                'Freq:0' : 2.0,
                'dBIPS/dPower:1' : 0.0,
            }
            for c in clients:
                nodeStatuses[c]['Limit:0'] = clusterPowerLimit/len(clients)/2
                nodeStatuses[c]['Limit:1'] = clusterPowerLimit/len(clients)/2
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
default_lr = 4.0
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
                default='360',help="cluster power limit")
    parser.add_argument("--periodms", type=float,
                default='2000',help="time period in milliseconds")
    parser.add_argument("--graceperiod", type=float,
                default='10',help="grace period in seconds")
    parser.add_argument("--duration", type=float,
                default='-1',help="experiment duration in seconds")
    args=parser.parse_args()
    signal.signal(signal.SIGINT, signal_handler)
    # Set bind address and port

    clusterPowerLimit = args.limit
    controllerserver = threading.Thread(target=ControllerServer)
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
            clients.sort()
            for c in clients:
                nodeStatuses[c]['Limit:0'] = clusterPowerLimit/len(clients)/2
                nodeStatuses[c]['Limit:1'] = clusterPowerLimit/len(clients)/2
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
            #b2p0 = (2*(totalbips/totalpower)*nodeStatuses[c]['dBIPS/dPower:0'] - (totalbips/totalpower)*(totalbips/totalpower))
            #b2p1 = (2*(totalbips/totalpower)*nodeStatuses[c]['dBIPS/dPower:1'] - (totalbips/totalpower)*(totalbips/totalpower))
            #b2p_grads[c] = (b2p0,b2p1)

        if args.policy == "hierarchical":
            sum_newpl = 0
            grad_sum=0
            
            for c in clients:
                grad_sum += b2p_grads[c][0] + b2p_grads[c][0]
                
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
                grad_sum += b2p_grads[c][0] + b2p_grads[c][1]
                
            if grad_sum > grad_max * len(clients):
                lr = default_lr * grad_max * len(clients)/grad_sum 
            elif grad_sum < -grad_max:
                lr = -default_lr * grad_max * len(clients)/grad_sum
            else:
                lr = default_lr

            for c in clients:
                curpl = nodeStatuses[c]['Limit:0']
                newpl = curpl - alpha*(curpl - nodeStatuses[c]['Consumption:0']) + lr*b2p_grads[c][0]
                newpl = max(power_min, newpl)
                newpl = min(power_max, newpl)
                sum_newpl += newpl
                nodeStatuses[c]['Limit:0'] = newpl

                curpl = nodeStatuses[c]['Limit:1']
                newpl = curpl - alpha*(curpl - nodeStatuses[c]['Consumption:1']) + lr*b2p_grads[c][1]
                newpl = max(power_min, newpl)
                newpl = min(power_max, newpl)
                sum_newpl += newpl
                nodeStatuses[c]['Limit:1'] = newpl
            remainder = 0
            eff_len = len(clients)*2
            coefs = {c:[1,1] for c in clients}
            if sum_newpl > clusterPowerLimit:
                delta = (sum_newpl - clusterPowerLimit)/len(clients)/2
                for c in clients:
                    newpl = nodeStatuses[c]['Limit:0'] - delta
                    
                    if nodeStatuses[c]['Freq:0'] < min_freq:
                        remainder += nodeStatuses[c]['Limit:0'] + 1 - newpl
                        eff_len = eff_len -1
                        newpl = nodeStatuses[c]['Limit:0'] + 1
                        coefs[c][0] = 0
                        '''
                    elif newpl < power_min:
                        remainder += power_min - newpl
                        eff_len = eff_len -1
                        newpl = power_min
                        coefs[c][0] = 0
                        '''
                    else:
                        coefs[c][0] = 1
                    nodeStatuses[c]['Limit:0'] = newpl
                    newpl = nodeStatuses[c]['Limit:1'] - delta
                    
                    if nodeStatuses[c]['Freq:1'] < min_freq:
                        remainder += nodeStatuses[c]['Limit:1'] + 1 - newpl
                        eff_len = eff_len -1
                        newpl = nodeStatuses[c]['Limit:1'] + 1
                        coefs[c][1] = 0
                    elif newpl < power_min:
                        remainder += power_min - newpl
                        eff_len = eff_len -1
                        newpl = power_min
                        coefs[c][1] = 0
                    else:
                        coefs[c][1] = 1
                    nodeStatuses[c]['Limit:1'] = newpl
                for c in clients:
                    if eff_len <= 0:
                        break
                    nodeStatuses[c]['Limit:0'] -= coefs[c][0]*remainder/eff_len
                    nodeStatuses[c]['Limit:1'] -= coefs[c][1]*remainder/eff_len
            
        elif args.policy == "geoml":
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

            geomb2p = 1

            for c in clients:
                b2p0 = 1.0 + nodeStatuses[c]['BIPS:0'] * nodeStatuses[c]['BIPS:0'] / nodeStatuses[c]['Consumption:0']
                b2p1 = 1.0 + nodeStatuses[c]['BIPS:1'] * nodeStatuses[c]['BIPS:1'] / nodeStatuses[c]['Consumption:1']
                geomb2p = geomb2p * ((b2p0 * b2p1) ** (0.5/len(clients)))
            for c in clients:
                curpl = nodeStatuses[c]['Limit:0']
                bpw = nodeStatuses[c]['BIPS:0'] / nodeStatuses[c]['Consumption:0']
                b2p = 1.0 + nodeStatuses[c]['BIPS:0'] * bpw
                grads0 = ((2*bpw*nodeStatuses[c]['dBIPS/dPower:0'] - bpw*bpw)) * geomb2p / b2p
                newpl = curpl - alpha*(curpl - nodeStatuses[c]['Consumption:0']) + lr*grads0
                newpl = max(power_min, newpl)
                newpl = min(power_max, newpl)
                sum_newpl += newpl
                nodeStatuses[c]['Limit:0'] = newpl

                curpl = nodeStatuses[c]['Limit:1']
                bpw = nodeStatuses[c]['BIPS:1'] / nodeStatuses[c]['Consumption:1']
                b2p = 1.0 + nodeStatuses[c]['BIPS:1'] * bpw
                grads1 = ((2*bpw*nodeStatuses[c]['dBIPS/dPower:1'] - bpw*bpw)) * geomb2p / b2p
                newpl = curpl - alpha*(curpl - nodeStatuses[c]['Consumption:1']) + lr*grads1
                newpl = max(power_min, newpl)
                newpl = min(power_max, newpl)
                sum_newpl += newpl
                nodeStatuses[c]['Limit:0'] = newpl
            remainder = 0
            eff_len = len(clients)*2
            coefs = {c:[1,1] for c in clients}
            if sum_newpl > clusterPowerLimit:
                delta = (sum_newpl - clusterPowerLimit)/len(clients)/2
                for c in clients:
                    newpl = nodeStatuses[c]['Limit:0'] - delta
                    if newpl < power_min:
                        remainder += power_min - newpl
                        eff_len = eff_len -1
                        newpl = power_min
                        coefs[c][0] = 0
                    elif nodeStatuses[c]['Freq:0'] < min_freq:
                        remainder += nodeStatuses[c]['Limit:0'] + 0.5 - newpl
                        eff_len = eff_len -1
                        newpl = nodeStatuses[c]['Limit:0'] + 0.5
                        coefs[c][0] = 0
                    else:
                        coefs[c][0] = 1
                    nodeStatuses[c]['Limit:0'] = newpl
                    newpl = nodeStatuses[c]['Limit:1'] - delta
                    if newpl < power_min:
                        remainder += power_min - newpl
                        eff_len = eff_len -1
                        newpl = power_min
                        coefs[c][1] = 0
                    elif nodeStatuses[c]['Freq:1'] < min_freq:
                        remainder += nodeStatuses[c]['Limit:1'] + 0.5 - newpl
                        eff_len = eff_len -1
                        newpl = nodeStatuses[c]['Limit:1'] + 0.5
                        coefs[c][1] = 0
                    else:
                        coefs[c][1] = 1
                    nodeStatuses[c]['Limit:1'] = newpl
                for c in clients:
                    if eff_len <= 0:
                        break
                    nodeStatuses[c]['Limit:0'] -= coefs[c][0]*remainder/eff_len
                    nodeStatuses[c]['Limit:1'] -= coefs[c][1]*remainder/eff_len
            else:
                delta = (sum_newpl - clusterPowerLimit)/len(clients)/2
                for c in clients:
                    nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] - delta/4
                    nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] - delta/4
            
        elif args.policy == 'slurm':
            pool = 0.0
            beta = len(clients) / (len(clients) - 0.99)
            for c in clients:
                diff = nodeStatuses[c]['Limit:0']-nodeStatuses[c]['Consumption:0']
                if diff>0.0:
                    pool += 0.5*diff* beta
                    nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] - 0.5*diff* beta
                diff = nodeStatuses[c]['Limit:1']-nodeStatuses[c]['Consumption:1']
                if diff>0.0:
                    pool += 0.5*diff* beta
                    nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] - 0.5*diff* beta
            for c in clients:
                nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] + pool/len(clients)/2
                nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] + pool/len(clients)/2
        elif args.policy == 'fair':
            for c in clients:
                nodeStatuses[c]['Limit:0'] = clusterPowerLimit/len(clients)/2
                nodeStatuses[c]['Limit:1'] = clusterPowerLimit/len(clients)/2
        elif args.policy == 'dps':
            # Restore unit
            initial_cap = clusterPowerLimit/len(clients)/2
            restore_flag = True
            for c in clients:
                if (nodeStatuses[c]['Consumption:0'] > initial_cap * inc_threshold or \
                    nodeStatuses[c]['Consumption:1'] > initial_cap * inc_threshold):
                    restore_flag = False
                    break

            # Stateless unit

            totalcap = 0
            for c in clients:
                if (nodeStatuses[c]['Consumption:0'] < nodeStatuses[c]['Limit:0'] * dec_threshold):
                    nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] * dec_percentile
                if (nodeStatuses[c]['Consumption:1'] < nodeStatuses[c]['Limit:1'] * dec_threshold):
                    nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] * dec_percentile
                totalcap += nodeStatuses[c]['Limit:0'] + nodeStatuses[c]['Limit:1']
            avail_budget = clusterPowerLimit - totalcap
            idxlist = list(range(len(clients)))
            random.shuffle(idxlist)
            for idx in idxlist:
                c = clients[idx]
                if (nodeStatuses[c]['Consumption:0'] > nodeStatuses[c]['Limit:0'] * inc_threshold):
                    tempt = min(avail_budget, nodeStatuses[c]['Limit:0'] * (inc_percentile - 1.0))
                    nodeStatuses[c]['Limit:0'] = nodeStatuses[c]['Limit:0'] + tempt
                    avail_budget -= tempt
                if (nodeStatuses[c]['Consumption:1'] > nodeStatuses[c]['Limit:1'] * inc_threshold):
                    tempt = min(avail_budget, nodeStatuses[c]['Limit:1'] * (inc_percentile - 1.0))
                    nodeStatuses[c]['Limit:1'] = nodeStatuses[c]['Limit:1'] + tempt
                    avail_budget -= tempt
            # Priority module
            priority_flags = {c:[False,False] for c in clients}
            for c in clients:
                peakratio[c][0] = peakratio[c][0] * 0.9
                if (nodeStatuses[c]['Consumption:0'] > initial_cap * inc_threshold):
                     peakratio[c][0] += 0.1
                peakratio[c][1] = peakratio[c][1] * 0.9
                if (nodeStatuses[c]['Consumption:1'] > initial_cap * inc_threshold):
                     peakratio[c][1] += 0.1

                if peakratio[c][0] > peak_threshold:
                    priority_flags[c][0] = True
                if peakratio[c][1] > peak_threshold:    
                    priority_flags[c][1] = True
                direv = nodeStatuses[c]['Consumption:0'] - prevpower[c][0]
                if direv > prevpower[c][0] * (inc_percentile - 1.0):
                    priority_flags[c][0] = True
                direv = nodeStatuses[c]['Consumption:1'] - prevpower[c][1]
                if direv > prevpower[c][1] * (inc_percentile - 1.0):
                    priority_flags[c][1] = True
            
            # Readjusting module
            budget_high = 0.0
            count_high = 0
            for c in clients:
                if priority_flags[c][0]:
                    budget_high += nodeStatuses[c]['Limit:0']
                    count_high += 1
                if priority_flags[c][1]:
                    budget_high += nodeStatuses[c]['Limit:1']
                    count_high += 1
            if avail_budget > 0:
                total = 0.0
                for c in clients:
                    if priority_flags[c][0]:
                        total += budget_high/nodeStatuses[c]['Limit:0']
                    if priority_flags[c][1]:
                        total += budget_high/nodeStatuses[c]['Limit:1']
                for c in clients:
                    if priority_flags[c][0]:
                        nodeStatuses[c]['Limit:0'] += avail_budget * budget_high/nodeStatuses[c]['Limit:0']/total
                        nodeStatuses[c]['Limit:0'] = min(nodeStatuses[c]['Limit:0'], power_max)
                    if priority_flags[c][1]:
                        nodeStatuses[c]['Limit:1'] += avail_budget * budget_high/nodeStatuses[c]['Limit:1']/total
                        nodeStatuses[c]['Limit:1'] = min(nodeStatuses[c]['Limit:1'], power_max)
            else:
                readjusted_cap = budget_high/count_high
                for c in clients:
                    if priority_flags[c][0]:
                        nodeStatuses[c]['Limit:0'] = readjusted_cap
                    if priority_flags[c][1]:
                        nodeStatuses[c]['Limit:1'] = readjusted_cap
            
            if restore_flag:
                for c in clients:
                    nodeStatuses[c]['Limit:0'] = initial_cap
                    nodeStatuses[c]['Limit:1'] = initial_cap
            
        elif args.policy == 'tokensmart':
            for c in clients:
                for soc in range(2):
                    fairModeEnabled = False
                    reqtokens = requiredTokens(nodeStatuses[c]['Util:' + str(soc)], 
                                               prevutils[c][soc], nodeStatuses[c]['BIPS:'+str(soc)], 
                                               prevbips[c][soc], tokens[c][soc], prevtokens[c][soc])
                    if reqtokens < tokens[c][soc]:
                        tokenpool = tokenpool + (tokens[c][soc] - reqtokens)
                    else:
                        if tokenpool <= 0:
                            starvationThreshold -= 1
                            if starvationThreshold <= 0:
                                fairModeEnabled = True
                            reqtokens = tokens[c][soc]
                        else:
                            extratokens = min(reqtokens - tokens[c][soc], tokenpool)
                            tokenpool -= extratokens
                            reqtokens = tokens[c][soc] + extratokens
                            starvationThreshold = 32
                            if fairModeEnabled:
                                fairModeEnabled = False
                    if fairModeEnabled:
                        if tokens[c][soc] > 12:
                            reqtokens = 12
                            tokenpool += tokens[c][soc] - 12
                        else:
                            reqtokens = tokens[c][soc]
                    prevtokens[c][soc] = tokens[c][soc]
                    tokens[c][soc] = reqtokens
                
            for c in clients:
                powrange = clusterPowerLimit/len(clients)/2 - power_min
                nodeStatuses[c]['Limit:0'] = 20 + powrange * tokens[c][0] / 12
                nodeStatuses[c]['Limit:1'] = 20 + powrange * tokens[c][1] / 12
                
        else:
            pass
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
    
