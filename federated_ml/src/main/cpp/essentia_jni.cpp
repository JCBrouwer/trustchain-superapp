#include <jni.h>
#include <string>
#include <essentia/essentia.h>
#include <essentia/algorithm.h>
#include <essentia/algorithmfactory.h>
#include <essentia/pool.h>
#include <essentia/utils/extractor_music/extractor_version.h>
#include <android/log.h>

using namespace std;
using namespace essentia;
using namespace essentia::standard;

void setExtractorDefaultOptions(Pool &options) {
  options.set("outputFrames", false);
  options.set("outputFormat", "json");
  options.set("requireMbid", false);
  options.set("indent", 2);

  options.set("highlevel.inputFormat", "json");
}

void setExtractorOptions(const std::string& filename, Pool& options) {
  if (filename.empty()) return;

  Pool opts;
  Algorithm * yaml = AlgorithmFactory::create("YamlInput", "filename", filename);
  yaml->output("pool").set(opts);
  yaml->compute();
  delete yaml;
  options.merge(opts, "replace");
}

void mergeValues(Pool& pool, Pool& options) {
  string mergeKeyPrefix = "mergeValues";
  vector<string> keys = options.descriptorNames(mergeKeyPrefix);

  for (int i=0; i<(int) keys.size(); ++i) {
    keys[i].replace(0, mergeKeyPrefix.size()+1, "");
    pool.set(keys[i], options.value<string>(mergeKeyPrefix + "." + keys[i]));
  }
}

void outputToFile(Pool& pool, const string& outputFilename, Pool& options) {
  cerr << "Writing results to file " << outputFilename << endl;
  int indent = (int)options.value<Real>("indent");

  string format = options.value<string>("outputFormat");
  Algorithm* output = AlgorithmFactory::create("YamlOutput",
                                               "filename", outputFilename,
                                               "doubleCheck", true,
                                               "format", format,
                                               "writeVersion", false,
                                               "indent", indent);
  output->input("pool").set(pool);
  output->compute();
  delete output;
}

int essentia_main(string audioFilename, string outputFilename, string profileName) {
    // Returns: 1 on essentia error

    try {
        essentia::init();
        setDebugLevel(EExecution);

        cout.precision(10);

        Pool options;
        setExtractorDefaultOptions(options);
        setExtractorOptions("", options);

        Algorithm* extractor = AlgorithmFactory::create("MusicExtractor", "profile", "");

        Pool results;
        Pool resultsFrames;

        extractor->input("filename").set(audioFilename);
        extractor->output("results").set(results);
        extractor->output("resultsFrames").set(resultsFrames);

        extractor->compute();

        mergeValues(results, options);

        outputToFile(results, outputFilename, options);
        if (options.value<Real>("outputFrames")) {
            outputToFile(resultsFrames, outputFilename+"_frames", options);
        }
        delete extractor;
        essentia::shutdown();
    }
    catch (EssentiaException& e) {
        __android_log_write(ANDROID_LOG_ERROR, "Essentia Android", e.what());
        return 1;
    }
    catch (const std::bad_alloc& e) {
        __android_log_write(ANDROID_LOG_ERROR, "Essentia Android", "bad_alloc exception: Out of memory");
        __android_log_write(ANDROID_LOG_ERROR, "Essentia Android", e.what());
        return 1;
    }

    return 0;
}

string convertJStringToString(JNIEnv *env, jstring str) {
    jboolean is_copy;
    const char *CString = env->GetStringUTFChars(str, &is_copy);
    return std::string(CString);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_federated_1ml_Essentia_extractData(JNIEnv *env, jclass,
        jstring input_path, jstring output_path) {
    std::string input = convertJStringToString(env, input_path);
    std::string output = convertJStringToString(env, output_path);
    int returnCode = essentia_main(input, output, "");
    return returnCode;
}