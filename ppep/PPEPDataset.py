import re
import numpy as np
import pandas as pd

def file_to_poly(fname):
    coefs = []
    with open(fname, 'r') as f:
        coefs = [float(l) for l in f]
    return np.poly1d(coefs)

def filter_active(df):
    df['avgutil:0'] = pd.Series(0.0, index=df.index)
    df['avgutil:1'] = pd.Series(0.0, index=df.index)
    for core in range(10):
        df['avgutil:0'] += df['cycle-count:'+str(core)] / (df['Freq(kHz):'+str(core)])
    for core in range(10):
        df['avgutil:1'] += df['cycle-count:'+str(core+10)] / (df['Freq(kHz):'+str(core+10)])
    df['avgutil:0'] = df['avgutil:0']/(10 * df['Duration(ms)'])
    df['avgutil:1'] = df['avgutil:1']/(10 * df['Duration(ms)'])
    #df['avgutil'].plot()
    mydf =  df[df['avgutil:0'] > 0.05]
    return mydf[mydf['avgutil:1'] > 0.05]

class PPEPRecord():
    def __init__(self, df, num_pkg=2, num_core=20):
        self.num_pkg = num_pkg
        self.core_per_pkg = num_core // num_pkg
        assert num_core%num_pkg == 0
        
        self.times = df['Time(ms)'].to_numpy()
        self.durations = df['Duration(ms)'].to_numpy()
        self.pkgcounters = ['DRAM Power(W)', 'Package Power(W)']
        self.counters = ['Voltage', 'Freq(kHz)', 'Temp(C)', 'instructions', 'cycle-count',
                        'cycle_activity.stalls_ldm_pending', 'uops_executed.core', 'branch-misses', 'cache-misses']
        self.stats = ['Util', 'MCPI', 'CCPI', 'BIPS', 'uop/inst', 'bmiss/inst', 'cmiss/inst']


        pkg_measures = [{} for _ in range(self.num_pkg)]
        core_measures = [{} for _ in range(self.num_pkg*self.core_per_pkg)]
        core_stats = [{} for _ in range(self.num_pkg*self.core_per_pkg)]
        for pctr in self.pkgcounters:
            for i in range(self.num_pkg):
                colname = pctr + ':' + str(i)
                pkg_measures[i][pctr] = df[colname].to_numpy()
        for ctr in self.counters:
            for i in range(self.core_per_pkg * self.num_pkg):
                colname = ctr + ':' + str(i)
                core_measures[i][ctr] = df[colname].to_numpy()
        for i in range(self.core_per_pkg * self.num_pkg):
            bips = core_measures[i]['instructions'] / self.durations / 1e6
            cpi = core_measures[i]['cycle-count'] / core_measures[i]['instructions']
            mcpi = core_measures[i]['cycle_activity.stalls_ldm_pending'] / core_measures[i]['instructions']
            ccpi = cpi - mcpi
            core_stats[i]['BIPS'] = bips
            core_stats[i]['Util'] = core_measures[i]['cycle-count'] / (core_measures[i]['Freq(kHz)'] * self.durations)
            core_stats[i]['MCPI'] = mcpi
            core_stats[i]['CCPI'] = ccpi
            core_stats[i]['uop/inst'] = core_measures[i]['uops_executed.core'] / core_measures[i]['instructions']
            core_stats[i]['bmiss/inst'] = core_measures[i]['branch-misses'] / core_measures[i]['instructions']
            core_stats[i]['cmiss/inst'] = core_measures[i]['cache-misses'] / core_measures[i]['instructions']

        carr = np.zeros((len(df), self.num_pkg, self.core_per_pkg, len(self.counters)))
        statarr = np.zeros((len(df), self.num_pkg, self.core_per_pkg, len(self.stats)))
        parr = np.zeros((len(df), self.num_pkg, len(self.pkgcounters)))
        for pnum in range(self.num_pkg):
            for i,pctr in enumerate(self.pkgcounters):
                parr[:,pnum,i] = pkg_measures[pnum][pctr]

            for cnum in range(self.core_per_pkg):
                for i,ctr in enumerate(self.counters):
                    carr[:,pnum,cnum,i] = core_measures[cnum+pnum*self.core_per_pkg][ctr]
                for i,stt in enumerate(self.stats):
                    statarr[:,pnum,cnum,i] = core_stats[cnum+pnum*self.core_per_pkg][stt]
        self.coredata = carr
        self.pkgdata = parr
        self.corestat = statarr

    def __len__(self):
        return len(self.pkgdata)

    def __getitem__(self, idx):
        
        return self.pkgdata[idx],self.coredata[idx],self.corestat[idx]
    

class PPEPData:
    def __init__(self, filenames, ifile='idlemodel.txt', vffile='vfpoly.txt'):
        self.voltages = []
        self.pstats = []
        self.power = []
        self.idlemodel = file_to_poly(ifile)
        print(self.idlemodel)
        self.vfmodel = file_to_poly(vffile)
        print(self.vfmodel)
        for fname in filenames:
            mydf = pd.read_csv(fname)
            mydf = filter_active(mydf)
            myrecord = PPEPRecord(mydf)
            
            pkgctrdata = myrecord.coredata.sum(axis=2)
            pkgstatdata = myrecord.corestat.mean(axis=2)
            
            pkgstatdata[:,:,3] *= 10 # bips is sum
            pkgctrdata[:,:,0] *= 0.1 # voltage is average
            pkgctrdata[:,:,1] *= 1e-7 # Freq is average and convert to ghz
            pkgctrdata[:,:,2] *= 0.1 # Temp is average

            pkgpower = myrecord.pkgdata[:,:,1]
            pkgvolt = pkgctrdata[:,:, 0]
            pkgbips = pkgstatdata[:,:,3]
            pkgutil = pkgstatdata[:,:,0]
            pkgbcps = pkgutil * pkgctrdata[:,:,1] * 10 # bcps is also sum
            pkgstats = pkgstatdata[:,:,4:] * pkgbips[:,:,None]
            pkgstats = np.concatenate([pkgstats, pkgbips[:,:,None], pkgbcps[:,:,None]], axis=-1)

            self.voltages += [pkgvolt[:,0], pkgvolt[:,1]]
            self.pstats += [pkgstats[:,0,:], pkgstats[:,1,:]]
            self.power += [pkgpower[:,0], pkgpower[:,1]]
        self.voltages = np.concatenate(self.voltages, axis=0)
        self.pstats = np.concatenate(self.pstats, axis=0)
        self.power = np.concatenate(self.power, axis=0)

if __name__ == '__main__':
    mydata = PPEPData(['gnn_tts_2600.csv', 'llama_stablediffusion_2600.csv'])
