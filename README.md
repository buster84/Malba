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
* [Malba-master.conf](Malba-master/src/main/resources/Malba-master.conf)
* [application.conf](Malba-master/src/main/resources/application.conf)
* More details. See [here](http://doc.akka.io/docs/akka/snapshot/general/configuration.html#config-akka-persistence)

###Start Malba
```sh
$ activator malba-master/run
```


How to use with Play!
---------------------
See this [project](sample/play2.3)

Slide
---------------------
* [Job queue in B2B with Akka](http://www.slideshare.net/YasukiOkumura/job-queue-in-b2b-with-akka-long-version)

## Under The Apache License, Version 2.0
http://www.apache.org/licenses/LICENSE-2.0.html

Copyright (c) shanon.co.jp
