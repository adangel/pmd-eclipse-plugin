#!/bin/bash
set -e

#Sample Usage: cleanup-bintray-snapshots.sh user apikey
API=https://api.bintray.com

if [ -z $BINTRAY_APIKEY -o -z $BINTRAY_USER ]; then
    # only take arguments, if there are no env variable set already
    BINTRAY_APIKEY=$1
    BINTRAY_USER=$2
fi

if [ -z $BINTRAY_APIKEY -o -z $BINTRAY_USER ]; then
  echo "Usage: $0 <BINTRAY_APIKEY> <BINTRAY_USER>"
  exit 1
fi

echo
echo Cleanup up old snapshot builds
echo ------------------------------
echo


BINTRAY_REPO=pmd-eclipse-plugin
BINTRAY_OWNER=pmd

#VERSION=4.6
VERSION=$(grep "<version>" pom.xml|head -1|sed -e 's/\s*<version>\([0-9]\{1,\}\.[0-9]\{1,\}\)\.[0-9]\{1,\}.\{1,\}<\/version>/\1/')
echo "  -> Cleaning up for version $VERSION"
echo
echo

BASE_PATH=pmd/pmd-eclipse-plugin/snapshots/updates/${VERSION}
BASE_URL=https://dl.bintray.com/${BASE_PATH}
BASE_PATH_SNAPSHOT_BUILDS=pmd/pmd-eclipse-plugin/snapshots/builds
BASE_PATH_SNAPSHOT_ZIPPED=pmd/pmd-eclipse-plugin/snapshots/zipped
ARTIFACTS_FILE=compositeArtifacts.xml
CONTENT_FILE=compositeContent.xml
WORKING_DIR=target/cleanup-bintray-snapshots
KEEP=5
#DRY_RUN=echo

mkdir -p ${WORKING_DIR}
pushd ${WORKING_DIR}


# download the metadata
curl ${BASE_URL}/${ARTIFACTS_FILE} > ${ARTIFACTS_FILE}
curl ${BASE_URL}/${CONTENT_FILE} > ${CONTENT_FILE}

# we keep some versions
artifacts=$(grep "<child location" ${ARTIFACTS_FILE} | tail -${KEEP})
#echo "Artifacts to keep:"
#echo "$artifacts"

count=$(grep "<child location" ${ARTIFACTS_FILE} | tail -${KEEP}|wc -l)
echo "Total artifacts count: ${count}"

artifacts_remove=$(grep "<child location" ${ARTIFACTS_FILE} | grep -v "${artifacts}" || true)
#echo "Artifacts to remove:"
#echo "$artifacts_remove"

artifacts_remove_count=$(echo "${artifacts_remove}" | grep -c "<child location" || true)
#echo "Artifacts to remove count: ${artifacts_remove_count}"

if [[ $artifacts_remove_count -eq 0 ]]; then
    echo "No artifacts should be removed, exiting"
    popd
    exit 0
fi


artifacts_versions=$(echo "${artifacts_remove}" | sed -n "s/ *<child location='..\/..\/builds\/\([^']*\).*/\1/p")
#echo "versions:"
#echo "$artifacts_versions"

for v in $artifacts_versions; do
    echo "Deleting $v ..."
    
    for file in artifacts.jar artifacts.xml.xz content.jar content.xml.xz p2.index features/net.sourceforge.pmd.eclipse_${v}.jar plugins/net.sourceforge.pmd.eclipse.plugin_${v}.jar; do
        path="${BASE_PATH_SNAPSHOT_BUILDS}/${v}/${file}"
        echo "Deleting ${path}"
        ${DRY_RUN} curl -X DELETE -u${BINTRAY_USER}:${BINTRAY_APIKEY} "https://api.bintray.com/content/${path}"
    done
    
    path="${BASE_PATH_SNAPSHOT_ZIPPED}/net.sourceforge.pmd.eclipse.p2updatesite-${v}.zip"
    echo "Deleting ${path}"
    ${DRY_RUN} curl -X DELETE -u${BINTRAY_USER}:${BINTRAY_APIKEY} "https://api.bintray.com/content/${path}"
    echo "Deleting version $v"
    ${DRY_RUN} curl -X DELETE -u${BINTRAY_USER}:${BINTRAY_APIKEY} "https://api.bintray.com/packages/pmd/pmd-eclipse-plugin/snapshots/versions/${v}"
    echo
    echo ----------------------------------------
done


echo
echo "Updating metadata"


artifactsTemplate="<?xml version='1.0' encoding='UTF-8'?>
<?compositeArtifactRepository version='1.0.0'?>
<repository name='PMD for Eclipse Update Site ${VERSION}' type='org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository' version='1.0.0'>
  <properties size='2'>
    <property name='p2.timestamp' value='$(date +%s)000'/>
    <property name='p2.atomic.composite.loading' value='true'/>
  </properties>
  <children size='${count}'>
$artifacts
  </children>
</repository>
"
echo "${artifactsTemplate}" > ${ARTIFACTS_FILE}.new
${DRY_RUN} curl -X PUT -u${BINTRAY_USER}:${BINTRAY_APIKEY} -T ${ARTIFACTS_FILE}.new https://api.bintray.com/content/${BASE_PATH}/${ARTIFACTS_FILE};publish=1


content=$(grep "<child location" ${CONTENT_FILE} | tail -${KEEP})
count=$(grep "<child location" ${CONTENT_FILE} | tail -${KEEP}|wc -l)
content_remove=$(grep "<child location" ${CONTENT_FILE} | grep -v "${content}")

#echo "Content to keep:"
#echo "$content"
#echo "Content to remove:"
#echo "$content_remove"

contentTemplate="<?xml version='1.0' encoding='UTF-8'?>
<?compositeMetadataRepository version='1.0.0'?>
<repository name='PMD for Eclipse Update Site ${VERSION}' type='org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository' version='1.0.0'>
  <properties size='2'>
    <property name='p2.timestamp' value='$(date +%s)000'/>
    <property name='p2.atomic.composite.loading' value='true'/>
  </properties>
  <children size='${count}'>
$content
  </children>
</repository>
"
echo "${contentTemplate}" > ${CONTENT_FILE}.new
${DRY_RUN} curl -X PUT -u${BINTRAY_USER}:${BINTRAY_APIKEY} -T ${CONTENT_FILE}.new https://api.bintray.com/content/${BASE_PATH}/${CONTENT_FILE};publish=1


popd
