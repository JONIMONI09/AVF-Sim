#include <jni.h>
#include <string>
#include <cstdlib>
#include <unistd.h>
#include <android/log.h>
#include <sys/wait.h>

extern "C" JNIEXPORT jint JNICALL
Java_com_example_MainActivity_executeNativeCommand(
        JNIEnv* env,
        jobject /* this */,
        jstring command) {
    const char *cmd = env->GetStringUTFChars(command, nullptr);
    
    __android_log_print(ANDROID_LOG_INFO, "AVFSimulator", "Native execute: %s", cmd);
    
    int result = system(cmd);
    
    env->ReleaseStringUTFChars(command, cmd);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_MainActivity_forkAndExec(
        JNIEnv* env,
        jobject /* this */,
        jstring command) {
    const char *cmd = env->GetStringUTFChars(command, nullptr);
    
    __android_log_print(ANDROID_LOG_INFO, "AVFSimulator", "Fork & Exec: %s", cmd);
    
    pid_t pid = fork();
    if (pid == 0) {
        // Child process
        execl("/system/bin/sh", "sh", "-c", cmd, nullptr);
        _exit(1);
    } else if (pid > 0) {
        // Parent process
        int status;
        waitpid(pid, &status, 0);
        env->ReleaseStringUTFChars(command, cmd);
        return WEXITSTATUS(status);
    }
    
    env->ReleaseStringUTFChars(command, cmd);
    return -1;
}
