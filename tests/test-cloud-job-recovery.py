"""Compile the exact production Job body without unrelated Android/API dependencies."""
import pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/org/openenglish/reader/CloudClient.java').read_text()
start=source.index('  public static class Job {');at=source.index('{',start);depth=1;end=at+1
while depth:
    if source[end]=='{':depth+=1
    elif source[end]=='}':depth-=1
    end+=1
target=root/'build/job-tests/source/org/openenglish/reader';target.mkdir(parents=True,exist_ok=True)
wrapper=target/'CloudClient.java'
wrapper.write_text('package org.openenglish.reader;\nimport java.net.*;import java.util.concurrent.CancellationException;\npublic final class CloudClient {\n'+source[start:end]+'\n}\n')
classes=root/'build/job-tests/classes';classes.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','--add-modules','jdk.compiler','com.sun.tools.javac.Main','-encoding','UTF-8','-source','8','-target','8','-d',str(classes),str(wrapper),str(root/'tests/CloudJobRecoveryTest.java'),str(root/'tests/CloudJobTest.java')],check=True)
subprocess.run(['java','-cp',str(classes),'org.openenglish.reader.CloudJobRecoveryTest'],check=True)
subprocess.run(['java','-cp',str(classes),'CloudJobTest'],check=True)
