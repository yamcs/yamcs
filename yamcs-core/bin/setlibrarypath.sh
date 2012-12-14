for d in /usr/lib /usr/lib64 /usr/local/lib /usr/local/lib64;
do 
   if [ -e $d/libjtokyocabinet.so ];
   then
      JAVA_LIBRARY_PATH=$JAVA_LIBRARY_PATH:$d   
   fi
done

if [ "$JAVA_LIBRARY_PATH" != "" ]
then
    JAVA_LIBRARY_PATH="-Djava.library.path=$JAVA_LIBRARY_PATH"
fi


