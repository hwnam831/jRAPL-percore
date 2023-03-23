#include <torch/script.h> // One-stop header.
#include "jtorch.h"
#include <iostream>
#include <memory>
#include <chrono>

JNIEXPORT void JNICALL Java_TorchTester_Test(JNIEnv * env, jclass jcls, jstring filename) {


  const char* fname = env->GetStringUTFChars(filename , NULL );

  torch::jit::script::Module module;
  try {
    // Deserialize the ScriptModule from a file using torch::jit::load().
    module = torch::jit::load(fname);
  }
  catch (const c10::Error& e) {
    std::cerr << "error loading the model\n";
    return;
  }
  at::Tensor x = torch::ones({1,1,2,10,9});


  std::vector<torch::jit::IValue> inputs;
  inputs.push_back(x);

  at::IValue output = module.forward(inputs);//avoid cold start
  output = module.forward(inputs);

  auto begin = std::chrono::steady_clock::now();
  for (int i = 0; i<100; i++){
    std::vector<torch::jit::IValue> ninputs;
    ninputs.push_back(x);

    output = module.forward(ninputs);

  }
  auto end = std::chrono::steady_clock::now();
  int duration = std::chrono::duration_cast<std::chrono::microseconds>(end - begin).count();
  std::cout << "Avg inference time = " << duration/100 << "µs" << '\n';
  at::Tensor out0 = output.toTensor();

  std::cout << "output val:\t" << out0.slice(/*dim=*/2, /*start=*/0, /*end=*/1) << '\n';
}