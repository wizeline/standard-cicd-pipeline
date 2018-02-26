#!/bin/bash

# SECRET_SLAVE_1=$(python - <<EOF
# import uuid
# print(str(uuid.uuid4()))
# EOF)
#
# SECRET_SLAVE_2=$(python - <<EOF
# import uuid
# print(str(uuid.uuid4()))
# EOF)

SECRET_SLAVE_1=f447cf73c6f5ef74b0d7745590d82893d5a86132b6977d184ae2a457183a9b02
SECRET_SLAVE_2=77a16b50be44d2b633243321359e253c5a9ce8045cd21e72b901e31707111852
JENKINS_OPTIONS="$(tr -d '\n' < options)"

kubectl -n jenkins delete secret jenkins
kubectl -n jenkins create secret generic jenkins \
        --from-literal=options="$JENKINS_OPTIONS" \
        --from-literal=secret.slave-1=$SECRET_SLAVE_1 \
        --from-literal=secret.slave-2=$SECRET_SLAVE_2
