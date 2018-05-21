# ProjectoCNV

zone : us-east-1
image name : ami-fd802a80
key name : CNV
security group : CNV-ssh-http

ssh -i CNV.pem -l ec2-user 52.73.2.166

CLASSPATH BIT
windows:
SET CLASSPATH=<path>BIT\BIT;<path>BIT\samples;.\

URL
http://<ip>:8000/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=75&s=bfs

#In case port is in use
sudo netstat -plten | grep java
sudo kill -9 <pid>


#things to run
sudo yum install git-core
alias goUp='cd /home/ec2-user/ProjectoCNV/src/'
alias goDown='cd /home/ec2-user/ProjectoCNV/src/MazeRunner/src/pt/ulisboa/tecnico/meic/cnv/mazerunner/maze'
alias compileAll='find . -name "*.java" -print | xargs javac'
export CLASSPATH="/home/ec2-user/ProjectoCNV/src/MazeRunner/src:/home/ec2-user/ProjectoCNV/src/BIT:/home/ec2-user/ProjectoCNV/src/BIT/samples:./"
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS
git clone https://github.com/nuno-santos96/ProjectoCNV
cd ProjectoCNV/src
compileAll
java OurTool MazeRunner/src/pt/ulisboa/tecnico/meic/cnv/mazerunner/maze
java WebServer


#post checkpoint
wget sdk-for-java.amazonwebservices.com/latest/aws-java-sdk.zip
unzip aws-java-sdk.zip
rm aws-java-sdk.zip

rm outputs/*
find . -type f -name '*.class' -delete
sudo /etc/rc.local start


export CLASSPATH="/home/ec2-user/ProjectoCNV/src/MazeRunner/src:/home/ec2-user/ProjectoCNV/src/BIT:/home/ec2-user/ProjectoCNV/src/BIT/samples:/home/ec2-user/ProjectoCNV/aws-java-sdk-1.11.333/lib/aws-java-sdk-1.11.333.jar:/home/ec2-user/ProjectoCNV/aws-java-sdk-1.11.333/third-party/lib/*:./"
alias compileAll='find . -name "*.java" -print | xargs javac'
export _JAVA_OPTIONS="-XX:-UseSplitVerifier "$_JAVA_OPTIONS

