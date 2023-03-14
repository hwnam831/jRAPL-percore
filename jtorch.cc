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
  at::Tensor x = torch::ones({1,32});
  at::Tensor h = torch::zeros({1,64});

  std::vector<torch::jit::IValue> inputs;
  inputs.push_back(x);
  inputs.push_back(h);
  at::IValue output = module.forward(inputs);//avoid cold start
  output = module.forward(inputs);
  h = output.toTuple()->elements()[1].toTensor();
  auto begin = std::chrono::steady_clock::now();
  for (int i = 0; i<100; i++){
    std::vector<torch::jit::IValue> ninputs;
    ninputs.push_back(x);
    ninputs.push_back(h);
    output = module.forward(ninputs);
    h = output.toTuple()->elements()[1].toTensor();
  }
  auto end = std::chrono::steady_clock::now();
  int duration = std::chrono::duration_cast<std::chrono::microseconds>(end - begin).count();
  std::cout << "Avg inference time = " << duration/100 << "µs" << '\n';
  at::Tensor out0 = output.toTuple()->elements()[0].toTensor();
  at::Scalar val = out0.item();
  std::cout << "output val:\t" << val.toFloat() << "\n";
}