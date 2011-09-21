#!/bin/bash

# Number of repos to create
USERS=5

# FIXME (CLC) work in a temporary folder


# Clone xebia-petclinic
git clone git@github.com:xebia-france-training/xebia-petclinic

CURRENT_GROUP_ID="<groupId>fr.xebia.demo.petclinic<\/groupId>"

for i in `jot - 1 $USERS`
do

	NEW_GROUP_ID="<groupId>fr.xebia.demo.petclinic-$i<\/groupId>"
	REPOSITORY_NAME="xebia-petclinic-$i"

	# create repository
	# FIXME (CLC) make github user a variable injected by an external file because xebia-continuous-delivery-tech-event can not be owner of the github account
	curl -u 'xebia-continuous-delivery-tech-event:1645faface' http://github.com/api/v2/json/repos/create -F name=xebia-france-training/$REPOSITORY_NAME

   	# Replace <groupId>fr.xebia.demo.petclinic</groupId> by <groupId>fr.xebia.demo.petclinic-$i</groupId> 	
	cp -rf xebia-petclinic/ $REPOSITORY_NAME
	sed "s/$CURRENT_GROUP_ID/$NEW_GROUP_ID/g" $REPOSITORY_NAME/pom.xml > /tmp/temppom.xml
	mv /tmp/temppom.xml $REPOSITORY_NAME/pom.xml

	# push sources from the cloned repository to the one just created
	cd $REPOSITORY_NAME
	git init
	git add .
	git commit -m "clone xebia-petclinic to $REPOSITORY_NAME"  
	git remote add $REPOSITORY_NAME git@github.com:xebia-france-training/$REPOSITORY_NAME.git
	git push -u $REPOSITORY_NAME master
	cd ..	

done