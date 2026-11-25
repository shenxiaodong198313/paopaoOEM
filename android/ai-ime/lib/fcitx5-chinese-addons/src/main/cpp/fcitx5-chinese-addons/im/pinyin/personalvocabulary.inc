// SPDX-License-Identifier: LGPL-2.1-or-later
#include <cmath>
#include <fcitx-utils/utf8.h>
namespace fcitx {
namespace pv = paopao::vocabulary;
const Configuration *PinyinEngine::getSubConfig(const std::string &path) const {
    return path == "paopao-vocabulary" ? &vocabularyResponse_ : nullptr;
}
pv::Records PinyinEngine::vocabularyRecords() {
    pv::Records records;
    const auto &trie = *ime_->dict()->trie(libime::PinyinDictionary::UserDict);
    std::string buffer;
    trie.foreach([&](float value,size_t length,libime::PinyinTrie::position_type pos){
        trie.suffix(buffer,length,pos);auto sep=buffer.find('!');if(sep==std::string::npos)return true;
        auto reading=libime::PinyinEncoder::decodeFullPinyin(buffer.data(),sep),text=buffer.substr(sep+1);
        std::ostringstream score;score.precision(9);score<<value;
        records["U\t"+reading+"\t"+text]={score.str(),text,reading,""};return true;
    });
    for(const auto &[text,code]:ime_->model()->history().vocabulary()) {
        if(text.empty()||text=="<s>"||text=="</s>")continue;
        std::string reading;
        try { if(!code.empty())reading=libime::PinyinEncoder::decodeFullPinyin(code); } catch (...) { continue; }
        records["H\t"+reading+"\t"+text]={"1",text,reading,""};
    }
    customPhrase_.foreach([&](const std::string &key,const std::vector<CustomPhrase> &values){
        for(const auto &v:values) if(!v.isDynamic())records["C\t"+key+"\t"+v.value()]={std::to_string(v.order()),v.value(),"",key};
    });
    return records;
}
void PinyinEngine::personalVocabulary(const RawConfig &request) {
    vocabularyResponse_.error.setValue("");vocabularyResponse_.requestId.setValue(pv::field(request,"RequestId"));
    auto root=StandardPaths::global().userDirectory(StandardPathsType::PkgData)/"pinyin";
    try {
        auto current=vocabularyRecords();
        if(pv::field(request,"Action")=="apply") {
            const auto changes=pv::patches(pv::field(request,"Data"),current);
            for(const auto &p:changes) {
                const auto k=pv::split(p.key,'\t');if(k.size()!=3)throw std::runtime_error("INVALID_KEY");pv::validText(k[2]);
                if(k[0]=="U"||(k[0]=="H"&&!k[1].empty())) {
                    auto encoded=libime::PinyinEncoder::encodeFullPinyinWithFlags(k[1],libime::PinyinFuzzyFlag::VE_UE);
                    if(encoded.size()!=size_t(utf8::length(k[2]))*2)throw std::runtime_error("INVALID_READING");
                } else if(k[0]=="C") {if(k[1].empty()||k[1].size()>32||k[1].find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")!=std::string::npos)throw std::runtime_error("INVALID_SHORTCUT");}
                else if(k[0]!="H")throw std::runtime_error("INVALID_KIND");
                if(p.hasAfter&&k[0]=="U"){auto score=std::stof(p.after);if(!std::isfinite(score)||score>0)throw std::runtime_error("INVALID_SCORE");}
                if(p.hasAfter&&k[0]=="C"){auto order=std::stoi(p.after);if(order==0)throw std::runtime_error("INVALID_ORDER");}
            }
            std::ostringstream dict,history,phrases;
            ime_->dict()->save(libime::PinyinDictionary::UserDict,dict,libime::PinyinDictFormat::Binary);
            ime_->model()->save(history);customPhrase_.save(phrases);
            const std::string backup=pv::hex(dict.str())+"\n"+pv::hex(history.str())+"\n"+pv::hex(phrases.str());
            if(!pv::writeFile(root/"personal-vocabulary.rollback",backup))throw std::runtime_error("BACKUP_FAILED");
            try {
                for(const auto &p:changes) {
                    const auto k=pv::split(p.key,'\t');
                    if(k[0]=="U") {if(p.hasAfter)ime_->dict()->addWord(libime::PinyinDictionary::UserDict,k[1],k[2],std::stof(p.after));else ime_->dict()->removeWord(libime::PinyinDictionary::UserDict,k[1],k[2]);}
                    if(k[0]=="H") {std::string code;if(!k[1].empty()){auto encoded=libime::PinyinEncoder::encodeFullPinyin(k[1]);code.assign(encoded.begin(),encoded.end());}ime_->model()->history().forget(k[2],code);if(p.hasAfter)ime_->model()->history().addWithCode(std::vector<libime::HistoryBigram::WordWithCode>{{k[2],code}});}
                    if(k[0]=="C") {customPhrase_.removePhrase(k[1],k[2]);if(p.hasAfter)customPhrase_.addPhrase(k[1],k[2],std::stoi(p.after));}
                }
                std::ostringstream newDict,newHistory,newPhrases;ime_->dict()->save(libime::PinyinDictionary::UserDict,newDict,libime::PinyinDictFormat::Binary);ime_->model()->save(newHistory);customPhrase_.save(newPhrases);
                if(!pv::writeFile(root/"user.dict",newDict.str())||!pv::writeFile(root/"user.history",newHistory.str())||!pv::writeFile(root/"customphrase",newPhrases.str()))throw std::runtime_error("PERSIST_FAILED");
                if(pv::readFile(root/"user.dict")!=newDict.str()||pv::readFile(root/"user.history")!=newHistory.str()||pv::readFile(root/"customphrase")!=newPhrases.str())throw std::runtime_error("PERSIST_VERIFY_FAILED");
                auto actual=vocabularyRecords();for(const auto &p:changes){auto found=actual.find(p.key);if((found!=actual.end())!=p.hasAfter)throw std::runtime_error("VERIFY_FAILED");}
                std::filesystem::remove(root/"personal-vocabulary.rollback");
            } catch (...) {
                std::istringstream d(dict.str()),h(history.str()),c(phrases.str());ime_->dict()->load(libime::PinyinDictionary::UserDict,d,libime::PinyinDictFormat::Binary);ime_->model()->load(h);customPhrase_.load(c,true);
                if(pv::writeFile(root/"user.dict",dict.str())&&pv::writeFile(root/"user.history",history.str())&&pv::writeFile(root/"customphrase",phrases.str()))std::filesystem::remove(root/"personal-vocabulary.rollback");
                throw;
            }
        }
        vocabularyResponse_.data.setValue(pv::serialize(vocabularyRecords()));
    } catch(const std::exception &) { vocabularyResponse_.error.setValue("词库已变化、读音不合法或保存失败，请刷新后重试");vocabularyResponse_.data.setValue(""); }
}
}
