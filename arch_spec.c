#include <stdio.h>
//#include <jni.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <math.h>
#include <stdint.h>
#include <string.h>
//#include "CPUScaler.h"
#include "arch_spec.h"

uint32_t eax, ebx, ecx, edx;
uint32_t cpu_model;

int read_time = 0;
uint64_t max_pkg = 0;
uint64_t num_core_thread = 0; 
uint64_t num_pkg_thread = 0;  
uint64_t num_pkg_core = 0; 
uint64_t num_pkg = 0; 
int core = 0;
int coreNum = 0;

cpuid_info_t cpu_info;

void
get_cpu_model(void)
{
    uint32_t eax = 0x01;
	CPUID;
    cpu_model = (((eax>>16)&0xFU)<<4) + ((eax>>4)&0xFU);
}

int core_num() {
	return sysconf(_SC_NPROCESSORS_CONF);
}



void 
parse_apic_id(cpuid_info_t info_l0, cpuid_info_t info_l1, APIC_ID_t *my_id){

	// Get the SMT ID
	uint64_t smt_mask_width = info_l0.eax & 0x1f;
	uint64_t smt_mask = ~((-1) << smt_mask_width);
	my_id->smt_id = info_l0.edx & smt_mask;

	// Get the core ID
	uint64_t core_mask_width = info_l1.eax & 0x1f;
	uint64_t core_mask = (~((-1) << core_mask_width ) ) ^ smt_mask;
	my_id->core_id = (info_l1.edx & core_mask) >> smt_mask_width;

	// Get the package ID
	uint64_t pkg_mask = (-1) << core_mask_width;
	my_id->pkg_id = (info_l1.edx & pkg_mask) >> core_mask_width;
}

void cpuid(uint32_t eax_in, uint32_t ecx_in, cpuid_info_t *ci) {
	 asm (
#if defined(__LP64__)           /* 64-bit architecture */
	     "cpuid;"                /* execute the cpuid instruction */
	     "movl %%ebx, %[ebx];"   /* save ebx output */
#else                           /* 32-bit architecture */
	     "pushl %%ebx;"          /* save ebx */
	     "cpuid;"                /* execute the cpuid instruction */
	     "movl %%ebx, %[ebx];"   /* save ebx output */
	     "popl %%ebx;"           /* restore ebx */
#endif
             : "=a"(ci->eax), [ebx] "=r"(ci->ebx), "=c"(ci->ecx), "=d"(ci->edx)
             : "a"(eax_in), "c"(ecx_in)
        );
}


cpuid_info_t getProcessorTopology(uint32_t level) {
	cpuid_info_t info;
	cpuid(0xb, level, &info);
	return info;	
}

int getSocketNum() {
	int i;
	uint32_t level1 = 0;
	uint32_t level2 = 1;
	cpuid_info_t info_l0;
	cpuid_info_t info_l1;
	//APIC_ID_t *os_map;
	//os_map = (APIC_ID_t *) malloc(coreNum * sizeof(APIC_ID_t));
	coreNum = core_num();
	//for(i = 0; i < coreNum; i++) {
		info_l0 = getProcessorTopology(level1);
		info_l1 = getProcessorTopology(level2);

//		os_map[i].os_id = i;
	//	parse_apic_id(info_l0, info_l1, &os_map[i]);
		//printf("my_id->pkg_id: %d\n", os_map[i].pkg_id);
		
		num_core_thread = info_l0.ebx & 0xffff;
		num_pkg_thread = info_l1.ebx & 0xffff;
/*
		if(os_map[i].pkg_id > max_pkg) {	//loop current pkg_id to ge pkg number
			max_pkg = os_map[i].pkg_id;
		}
		*/
	//	printf("my_id->pkg_id: %d, max_pkg: %d\n", os_map[i].pkg_id, max_pkg);
		
	//}
	
	num_pkg_core = num_pkg_thread / num_core_thread;
	num_pkg = coreNum / num_pkg_thread;
	#ifdef NOSMT
		coreNum = coreNum/num_core_thread;
		num_core_thread=1;
	#endif
	return num_pkg;

	//printf("num_pkg_thread: %d, num_core_thread: %d, coreNum: %d, num_pkg: %d\n", num_pkg_thread, num_core_thread, coreNum, num_pkg);
//	return num_pkg;
}
