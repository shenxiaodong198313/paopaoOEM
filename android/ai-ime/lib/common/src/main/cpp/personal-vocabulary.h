// SPDX-License-Identifier: LGPL-2.1-or-later
#pragma once
#include <fcitx-config/configuration.h>
#include <fcitx-config/option.h>
#include <fcitx-config/rawconfig.h>
#include <fcitx-utils/standardpaths.h>
#include <fstream>
#include <map>
#include <sstream>
#include <stdexcept>
#include <vector>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <filesystem>

namespace paopao::vocabulary {
FCITX_CONFIGURATION(Response,
    fcitx::Option<std::string> data{this, "Data", "Data", ""};
    fcitx::Option<std::string> error{this, "Error", "Error", ""};
    fcitx::Option<std::string> errorCode{this, "ErrorCode", "ErrorCode", ""};
    fcitx::Option<std::string> requestId{this, "RequestId", "RequestId", ""};);
struct Record { std::string value, text, reading, shortcut; };
using Records = std::map<std::string, Record>;
struct Patch { std::string key, before, after; bool hadBefore, hasAfter; };
inline std::vector<std::string> split(const std::string &s, char sep) {
    std::vector<std::string> result; size_t start=0;
    while (true) { auto end=s.find(sep,start); result.push_back(s.substr(start,end==std::string::npos?end:end-start)); if(end==std::string::npos)break;start=end+1; }
    return result;
}
inline std::string hex(const std::string &s) { const char *chars="0123456789abcdef";std::string out;out.reserve(s.size()*2);for(unsigned char c:s){out+=chars[c>>4];out+=chars[c&15];}return out; }
inline std::string unhex(const std::string &s) { if(s.size()%2)throw std::runtime_error("INVALID_HEX");std::string out;auto n=[](char c){if(c>='0'&&c<='9')return c-'0';if(c>='a'&&c<='f')return c-'a'+10;throw std::runtime_error("INVALID_HEX");};for(size_t i=0;i<s.size();i+=2)out+=char(n(s[i])*16+n(s[i+1]));return out; }
inline std::string field(const fcitx::RawConfig &c,const char *name){auto p=c.get(name);return p?p->value():"";}
inline std::string serialize(const Records &records){std::string out;for(const auto &[key,r]:records)out+=hex(key)+"\t"+hex(r.value)+"\t"+hex(r.text)+"\t"+hex(r.reading)+"\t"+hex(r.shortcut)+"\n";return out;}
inline std::vector<Patch> patches(const std::string &data,const Records &current){
    if(data.size()>4*1024*1024)throw std::runtime_error("REQUEST_TOO_LARGE");
    std::vector<Patch> out;std::map<std::string,bool> seen;
    for(const auto &line:split(data,'\n')) {if(line.empty())continue;auto a=split(line,'\t');if(a.size()!=3)throw std::runtime_error("INVALID_PATCH");Patch p{unhex(a[0]),a[1]=="-"?"":unhex(a[1]),a[2]=="-"?"":unhex(a[2]),a[1]!="-",a[2]!="-"};if(!seen.emplace(p.key,true).second)throw std::runtime_error("DUPLICATE_PATCH");auto old=current.find(p.key);if(p.hadBefore!=(old!=current.end())||(p.hadBefore&&old->second.value!=p.before))throw std::runtime_error("VOCABULARY_CONFLICT");out.push_back(std::move(p));}
    return out;
}
inline bool writeFile(const std::filesystem::path &p,const std::string &data){
    std::filesystem::create_directories(p.parent_path());auto tmp=p;tmp+=".new";
    int fd=::open(tmp.c_str(),O_WRONLY|O_CREAT|O_TRUNC|O_CLOEXEC,0600);if(fd<0)return false;
    size_t written=0;bool ok=true;
    while(written<data.size()){auto n=::write(fd,data.data()+written,data.size()-written);if(n<0&&errno==EINTR)continue;if(n<=0){ok=false;break;}written+=n;}
    if(ok)ok=::fsync(fd)==0;::close(fd);if(!ok)return false;
    std::error_code error;std::filesystem::rename(tmp,p,error);if(error)return false;
    int directory=::open(p.parent_path().c_str(),O_RDONLY|O_DIRECTORY|O_CLOEXEC);if(directory<0)return false;
    ok=::fsync(directory)==0;::close(directory);return ok;
}
inline std::string readFile(const std::filesystem::path &p){std::ifstream in(p,std::ios::binary);if(!in)throw std::runtime_error("READ_FAILED");return {std::istreambuf_iterator<char>(in),{}};}
inline void validText(const std::string &s){if(s.empty()||s.size()>512||s.find_first_of("\t\r\n")!=std::string::npos||s.find('\0')!=std::string::npos)throw std::runtime_error("INVALID_WORD");}
}
