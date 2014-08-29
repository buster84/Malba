Malba
=====
Job Queue service writtten by Scala + [Akka](http://akka.io/). 
Main features are:
* You can add your own Remote Actor either akka2.2.x and akka2.3.x as workers dynamically.
* This allows to use your own custom queue of jobs.
* It is easy to use with Play2.2.x and Play2.3.x.
* You can add and get tasks by http protocal( in the near future. As you can see the [protocol](Malba-protocol/src/main/scala/jp/co/shanon/malba/worker/MalbaProtcol.scala), it is not so difficult. )  


How to use 
--------------------
###Change configuration

###Start Malba
```sh
$ activator malba-master/run
```

## Under The Apache License, Version 2.0
http://www.apache.org/licenses/LICENSE-2.0.html

Copyright (c) shanon.co.jp
