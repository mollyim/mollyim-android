#ifndef MOLLY_TRANSLATION_ENGINE_H
#define MOLLY_TRANSLATION_ENGINE_H

#include <string>
#include <vector>
#include <memory>

namespace molly {
namespace translation {

struct TranslationResult {
    std::string translated_text;
    float confidence;
    uint64_t inference_time_us;
    bool used_network;
};

class TranslationEngine {
public:
    TranslationEngine();
    ~TranslationEngine();

    bool initialize(const std::string& model_path);
    TranslationResult translate(const std::string& source_text,
                                const std::string& source_lang,
                                const std::string& target_lang);

    bool is_model_loaded() const { return model_loaded_; }

private:
    bool model_loaded_;
    std::string model_path_;

    // Stub for actual model inference
    std::string perform_inference(const std::string& text);
};

} // namespace translation
} // namespace molly

#endif // MOLLY_TRANSLATION_ENGINE_H
