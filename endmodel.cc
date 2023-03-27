#include <torch/script.h> // One-stop header.
#include "MLModel.h"
#include <iostream>
#include <memory>
#include <chrono>

torch::jit::script::Module powermodule;
torch::jit::script::Module bipsmodule;

JNIEXPORT void JNICALL Java_MLModel_init
  (JNIEnv * env, jclass cls, jstring fname_power, jstring fname_bips){

    torch::jit::script::Module module;
    try {
      // Deserialize the ScriptModule from a file using torch::jit::load().
      powermodule = torch::jit::load(env->GetStringUTFChars(fname_power , NULL ));
      bipsmodule = torch::jit::load(env->GetStringUTFChars(fname_bips , NULL ));
    }
    catch (const c10::Error& e) {
      std::cerr << "error loading the models\n";
      return;
    }

  }

/*
 * Class:     MLModel
 * Method:    close
 * Signature: ()V

JNIEXPORT void JNICALL Java_MLModel_close
  (JNIEnv * env, jclass cls){
    powermodule.
  }
 */
/*
 * Class:     MLModel
 * Method:    forward
 * Signature: ([F)[F
 */
JNIEXPORT jfloatArray JNICALL Java_MLModel_forward
  (JNIEnv * env, jclass cls, jfloatArray flat_input){
    float coefs[7];
    jfloat *flat_arr = env->GetFloatArrayElements(flat_input, NULL);
    float c_arr[2*10*9];
    for (int i=0; i<2*10*9; i++){
      c_arr[i] = flat_arr[i];
    }
    env->ReleaseFloatArrayElements(flat_input, flat_arr, 0);
    torch::Tensor input_ten = torch::from_blob(c_arr, {1,1,2,10,9}, torch::TensorOptions().dtype(torch::kFloat32));
    std::cout << input_ten.slice(3,0,1,1) << std::endl;
    std::vector<torch::jit::IValue> inputs;
    inputs.push_back(input_ten);
    auto power_coefs = powermodule.forward(inputs);
    auto bips_coefs = bipsmodule.forward(inputs);
    std::cout << power_coefs << std::endl;
    jfloatArray arr = env->NewFloatArray(7);
    env->SetFloatArrayRegion(arr, 0, 7, coefs);
    return arr;
  }