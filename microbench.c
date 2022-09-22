#include <stdio.h>
#include <stdlib.h>
#define N 2048
#define T 4096*1024

int main(int argc, char** argv){
    double sum;

    double a[N];
    double b[N];
    double c[N];
    int i,e;
    for (i=0; i<N; i++){
        a[i] = (double)(rand()%512)/256;
        b[i] = (double)(rand()%512)/256;
        c[i] = 0.0;
    }


    for (e=0; e<T; e++){
        for(i=0; i<N; i++){
            c[i] = c[i] + 0.1*a[i]*b[i];
        }
    }

    for (i=0; i<N; i++){
        sum += c[i];
    }
    printf("%f\n", sum);
    return (int)sum;
}