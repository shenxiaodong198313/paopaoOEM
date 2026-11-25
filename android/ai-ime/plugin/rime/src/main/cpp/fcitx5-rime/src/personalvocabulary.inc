// SPDX-License-Identifier: LGPL-2.1-or-later
// librime 1.16.1 LevelDb/UserDbValue format. Only accessed with Rime finalized.
#include <leveldb/db.h>
#include <leveldb/write_batch.h>
#include <regex>
namespace fcitx::rime {
namespace pv=paopao::vocabulary;
using Databases=std::map<std::string,std::unique_ptr<leveldb::DB>>;
static Databases vocabularyDatabases(const std::filesystem::path &root) {
    Databases result;for(const auto &entry:std::filesystem::directory_iterator(root)) {
        auto name=entry.path().filename().string();if(!entry.is_directory()||(name.size()<7||name.compare(name.size()-7,7,".userdb")!=0))continue;
        auto stem=name.substr(0,name.size()-7);if(!std::regex_match(stem,std::regex("[a-zA-Z0-9_-]+")))continue;
        leveldb::DB *db=nullptr;leveldb::Options options;options.create_if_missing=false;
        if(!leveldb::DB::Open(options,entry.path().string(),&db).ok())throw std::runtime_error("RIME_DB_BUSY");result[stem].reset(db);
    }return result;
}
static int commits(const std::string &v){std::smatch match;if(!std::regex_search(v,match,std::regex("(?:^| )c=(-?[0-9]+)(?: |$)")))return -1;return std::stoi(match[1]);}
static pv::Records rimeRecords(Databases &dbs,const std::filesystem::path &root) {
    pv::Records rows;for(auto &[name,db]:dbs){std::unique_ptr<leveldb::Iterator> it(db->NewIterator({}));for(it->SeekToFirst();it->Valid();it->Next()){
        auto key=it->key().ToString(),value=it->value().ToString();if(key.empty()||key[0]=='\1'||commits(value)<0)continue;
        auto sep=key.find('\t');if(sep==std::string::npos)continue;auto text=key.substr(sep+1),reading=key.substr(0,sep);
        while(!reading.empty()&&reading.back()==' ')reading.pop_back();rows["U\t"+name+"\t"+key]={value,text,reading,""};
    }if(!it->status().ok())throw std::runtime_error("READ_FAILED");}
    auto file=root/"custom_phrase_t9.txt";if(std::filesystem::exists(file))for(const auto &line:pv::split(pv::readFile(file),'\n')){
        if(line.empty()||line[0]=='#')continue;auto f=pv::split(line,'\t');if(f.size()<2)continue;auto order=f.size()>2?f[2]:"1";if(!order.empty()&&order.back()=='\r')order.pop_back();rows["C\t"+f[1]+"\t"+f[0]]={order,f[0],"",f[1]};
    }return rows;
}
void recoverPersonalVocabulary(const std::filesystem::path &root) {
    auto journal=root/"personal-vocabulary.rollback";if(!std::filesystem::exists(journal))return;
    auto dbs=vocabularyDatabases(root);for(const auto &line:pv::split(pv::readFile(journal),'\n')){
        if(line.empty())continue;auto f=pv::split(line,'\t');if(f.size()!=3)throw std::runtime_error("BAD_RECOVERY");auto name=pv::unhex(f[0]);
        if(name=="@phrases"){if(!pv::writeFile(root/"custom_phrase_t9.txt",pv::unhex(f[2])))throw std::runtime_error("RECOVERY_FAILED");continue;}
        auto db=dbs.find(name);if(db==dbs.end())throw std::runtime_error("DB_MISSING");leveldb::WriteOptions options;options.sync=true;auto key=pv::unhex(f[1]);auto status=f[2]=="-"?db->second->Delete(options,key):db->second->Put(options,key,pv::unhex(f[2]));if(!status.ok())throw std::runtime_error("RECOVERY_FAILED");
    }dbs.clear();std::filesystem::remove(journal);
}
const Configuration *RimeEngine::getSubConfig(const std::string &path) const {
    if (path == "paopao-vocabulary") return &vocabularyResponse_;
    if (path == "paopao-schema") return &schemaResponse_;
    if (path == "paopao-prediction") return &predictionResponse_;
    return nullptr;
}
void RimeEngine::personalVocabulary(const RawConfig &request) {
    vocabularyResponse_.error.setValue("");vocabularyResponse_.errorCode.setValue("");vocabularyResponse_.requestId.setValue(pv::field(request,"RequestId"));
    auto root=StandardPaths::global().userDirectory(StandardPathsType::PkgData)/"rime";
    bool stopped=false;
    try {
        // Recheck on the engine thread, closing the race with a newly focused editor.
        if(pv::field(request,"OnlyWhenIdle")=="true"&&instance_->inputContextManager().lastFocusedInputContext())throw std::runtime_error("INPUT_ACTIVE");
        if(api_->is_maintenance_mode())throw std::runtime_error("RIME_BUSY");
        // Instantiate the supported schema without sending keys or learning any text.
        if(!std::filesystem::exists(root/"rime_ice.userdb")) {
            const auto session=api_->create_session();
            if(session) {
                char previousSchema[256]{};
                const bool hadSchema=api_->get_current_schema(session,previousSchema,sizeof(previousSchema));
                api_->select_schema(session,"t9");
                if(hadSchema&&previousSchema[0])api_->select_schema(session,previousSchema);
                api_->destroy_session(session);
            }
        }
        releaseAllSession(true);api_->finalize();stopped=true;
        auto dbs=vocabularyDatabases(root);auto current=rimeRecords(dbs,root);
        if(pv::field(request,"Action")=="apply") {
            const auto changes=pv::patches(pv::field(request,"Data"),current);
            std::map<std::string,leveldb::WriteBatch> batches;std::string rollback;
            const auto phraseFile=root/"custom_phrase_t9.txt";auto oldPhrases=std::filesystem::exists(phraseFile)?pv::readFile(phraseFile):"";
            bool phrasesChanged=false;
            for(const auto &p:changes){auto f=pv::split(p.key,'\t');
                if(f.size()==4&&f[0]=="U"){
                    auto db=dbs.find(f[1]);if(db==dbs.end())throw std::runtime_error("UNSUPPORTED_DICTIONARY");pv::validText(f[3]);
                    if(f[2].empty()||f[2].size()>256||!std::regex_match(f[2],std::regex("[a-zv]+( [a-zv]+)* ")))throw std::runtime_error("INVALID_READING");
                    const auto key=f[2]+"\t"+f[3];std::string old;const auto got=db->second->Get({},key,&old);if(!got.ok()&&!got.IsNotFound())throw std::runtime_error("READ_FAILED");rollback+=pv::hex(f[1])+"\t"+pv::hex(key)+"\t"+(got.ok()?pv::hex(old):"-")+"\n";
                    if(p.hasAfter){if(!std::regex_match(p.after,std::regex("c=[0-9]+ d=[0-9.eE+-]+ t=[0-9]+")))throw std::runtime_error("INVALID_VALUE");batches[f[1]].Put(key,p.after);}
                    else batches[f[1]].Put(key,std::regex_replace(old,std::regex("c=-?[0-9]+"),"c=-1"));
                } else if(f.size()==3&&f[0]=="C") {
                    pv::validText(f[2]);if(!std::regex_match(f[1],std::regex("[2-9]{1,32}")))throw std::runtime_error("INVALID_SHORTCUT");
                    if(p.hasAfter&&(!std::regex_match(p.after,std::regex("[0-9]{1,8}"))||std::stoi(p.after)<1))throw std::runtime_error("INVALID_ORDER");
                    phrasesChanged=true;if(p.hasAfter)current[p.key]={p.after,f[2],"",f[1]};else current.erase(p.key);
                } else throw std::runtime_error("INVALID_KEY");
            }
            if(phrasesChanged)rollback+=pv::hex("@phrases")+"\t\t"+pv::hex(oldPhrases)+"\n";
            if(!pv::writeFile(root/"personal-vocabulary.rollback",rollback))throw std::runtime_error("BACKUP_FAILED");
            leveldb::WriteOptions options;options.sync=true;
            for(auto &[name,batch]:batches)if(!dbs.at(name)->Write(options,&batch).ok())throw std::runtime_error("WRITE_FAILED");
            if(phrasesChanged){
                std::map<std::string,bool> touched;
                for(const auto &p:changes)if(p.key.rfind("C\t",0)==0)touched[p.key]=true;
                std::string output;
                size_t cursor=0;
                while(cursor<oldPhrases.size()){
                    auto end=oldPhrases.find('\n',cursor);
                    auto length=end==std::string::npos?oldPhrases.size()-cursor:end-cursor+1;
                    auto line=oldPhrases.substr(cursor,length);auto parsed=line;while(!parsed.empty()&&(parsed.back()=='\n'||parsed.back()=='\r'))parsed.pop_back();auto f=pv::split(parsed,'\t');
                    if(f.size()<2||!touched.count("C\t"+f[1]+"\t"+f[0]))output+=line;
                    cursor+=length;
                }
                if(!output.empty()&&output.back()!='\n')output+='\n';
                for(const auto &p:changes)if(p.key.rfind("C\t",0)==0&&p.hasAfter){const auto &r=current.at(p.key);output+=r.text+"\t"+r.shortcut+"\t"+r.value+"\n";}
                if(!pv::writeFile(phraseFile,output))throw std::runtime_error("WRITE_FAILED");
            }
            auto actual=rimeRecords(dbs,root);for(const auto &p:changes){auto it=actual.find(p.key);if((it!=actual.end())!=p.hasAfter||(p.hasAfter&&it->second.value!=p.after))throw std::runtime_error("VERIFY_FAILED");}
            std::filesystem::remove(root/"personal-vocabulary.rollback");
        }
        vocabularyResponse_.data.setValue(pv::serialize(rimeRecords(dbs,root)));dbs.clear();rimeStart(false,false);stopped=false;
    } catch(const std::exception &error) {
        const std::string code=error.what();vocabularyResponse_.errorCode.setValue(std::regex_match(code,std::regex("[A-Z_]{1,40}"))?code:"RIME_OPERATION_FAILED");
        vocabularyResponse_.error.setValue("九宫格词库忙碌、已变化或保存失败，请稍后刷新重试");vocabularyResponse_.data.setValue("");
        if(stopped){try {recoverPersonalVocabulary(root);rimeStart(false,false);}catch(...){vocabularyResponse_.error.setValue("九宫格词库需要恢复，请重启输入法后重试");}}
    }
}
}
