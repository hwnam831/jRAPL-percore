#include <torch/script.h> // One-stop header.
#include "MLModel.h"
#include <iostream>
#include <memory>
#include <chrono>


JNIEXPORT void JNICALL Java_MLModel_init
  (JNIEnv * env, jclass cls, jstring fname_power, jstring fname_bips){


  }

/*
 * Class:     MLModel
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_MLModel_close
  (JNIEnv * env, jclass cls){

  }

/*
 * Class:     MLModel
 * Method:    forward
 * Signature: ([F)[F
 */
JNIEXPORT jfloatArray JNICALL Java_MLModel_forward
  (JNIEnv * env, jclass cls, jfloatArray flat_input){
    float coefs[7];
    return coefs;
  }