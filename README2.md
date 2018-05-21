# ProjectoCNV

Instructions to create instance image:

	- Create instance and connect to instance
	- Update packages and set java 7
	- Place this folder inside the instance
	- Copy contents of Instance_rc.local to /etc/rc.local
	- Download AWS SDK to this folder (as in the classpath defined in the rc.local)
	- Download MazeRunner to this folder (as in the classpath defined in the rc.local)
	- Create .aws folder with credentials in ec2-user folder
	- Run sudo /etc/rc.local to test instance
	- Create Image

Instructions to create LoadBalancer image:

	- Create instance and connect to instance
	- Update packages and set java 7
	- Place this folder inside the instance
	- Copy contents of LB_rc.local to /etc/rc.local
	- Download AWS SDK to this folder (as in the classpath defined in the rc.local)
	- Create .aws folder with credentials in ec2-user folder
	- Edit the AWS_SECURITY_GROUP, IMAGE_ID, AWS_KEY constants in LoadBalancer.java
	- Run sudo /etc/rc.local to test instance
	- Create Image

#In case port is in use
sudo netstat -plten | grep java
sudo kill <pid>

