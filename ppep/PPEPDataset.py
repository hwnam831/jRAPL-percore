import re
import numpy as np

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
        self.stats = ['BIPS', 'Util', 'ldm-stalls', 'uops', 'bmiss', 'cmiss']
        self.coredata = []
        self.pkgdata = []

        pkg_measures = [{} for _ in range(self.num_pkg)]
        core_measures = [{} for _ in range(self.num_pkg*self.core_per_pkg)]
        for pctr in self.pkgcounters:
            for i in range(self.num_pkg):
                colname = pctr + ':' + str(i)
                pkg_measures[i][pctr] = df[colname].to_numpy()
        for ctr in self.counters:
            for i in range(self.core_per_pkg * self.num_pkg):
                colname = ctr + ':' + str(i)
                core_measures[i][ctr] = df[colname].to_numpy()
        
        for idx in range(len(df)):

            carr = np.zeros((len(df), self.num_pkg, self.core_per_pkg, len(self.counters)))
            parr = np.zeros((len(df), self.num_pkg, len(self.pkgcounters)))
            for pnum in range(self.num_pkg):
                for i,pctr in enumerate(self.pkgcounters):
                    parr[:,pnum,i] = pkg_measures[pnum][pctr][start:end]

                for cnum in range(self.core_per_pkg):
                    for i,ctr in enumerate(self.counters):
                        carr[:,pnum,cnum,i] = core_measures[cnum+pnum*self.core_per_pkg][ctr][start:end]
            self.coredata.append(carr)
            self.pkgdata.append(parr)

    def __len__(self):
        return len(self.pkgdata)

    def __getitem__(self, idx):
        
        return self.coredata[idx],self.pkgdata[idx]