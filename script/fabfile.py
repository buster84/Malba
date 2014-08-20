import sys, os, re , string , random , time
from fabric.api import *
from fabric.contrib.files import exists
from fabric.utils import abort

def getenv(name):
    if os.environ.has_key(name):
        return os.environ[name]
    return ""

def getenvWithDefault(name, default):
    if os.environ.has_key(name):
        return os.environ[name]
    else:
        return default

java_home    = getenv('JAVA_HOME')
mem          = getenv('MEM')
env.user     = getenv('SSH_USER')
env.password = getenv('SSH_PASSWORD')
prog         = getenv('SERVICE_NAME')
run_user     = getenv('RUN_USER')
artifact_url = getenv('PACKAGE_URL')
akka_port    = getenv('AKKA_PORT')

start_script = getenvWithDefault('START_SCRIPT', '/usr/bin/%s' % (prog))
piddir       = getenvWithDefault('PIDDIR'      , '/var/run/%s/' % (prog))
pidfile      = getenvWithDefault('PIDFILE'     , '/var/run/%s/running.pid' % (prog))
mode         = getenvWithDefault('MODE'        , 'prod')

def randstrings():
    str = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz123456789"
    strlist = list(str)
    string = ""

    for i in range(20):
        x = random.randint(0,len(strlist)-1)
        string += strlist[x]

    return string

def deploy():
    monit_monitor('unmonitor')
    if checkStatus():
        stop()
    update()
    start()
    if not checkStatus():
        abort('Can not run %s' % (prog))
    time.sleep(60.0)
    monit_monitor('monitor')

def monit_monitor(mode):
    run('monit %s %s' % (mode,prog))


def stop():
    run('service %s stop' % (prog))
    time.sleep(10.0)
    if checkStatus():
        abort('Can not stop %s' % (prog))
    else:
        return

def update():
    workspace    = '/tmp/%s_%s' % (prog, randstrings())
    run('mkdir -p %s' % (workspace))
    with cd(workspace):
        run('wget %s' % (artifact_url))
        run('yum install -y ./*rpm')
        run('echo " -java-home %s" >> %s' % (java_home, prog))
        run('echo " -mem %s" >> %s' % (mem, prog))
        run('echo " -Dconfig.resource=%s" >> %s' % ("application_" + mode + ".conf", prog))
        run('echo " -Dlogback.configurationFile=%s" >> %s' % ("logger_" + mode + ".xml", prog))
        run('echo " -DAKKA_PORT=%s" >> %s' % (akka_port, prog))
        run('echo " -DSELF_IP=%s" >> %s' % (env.host, prog))
        run('mv %s /etc/default/' % (prog))
    run('rm -rf %s' % (workspace))

def start():
    run('service %s start ' % (prog))

def restart():
    monit_monitor('unmonitor')
    if checkStatus():
        stop()
    start()
    if not checkStatus():
        abort('Can not run %s' % (prog))
    time.sleep(60.0)
    monit_monitor('monitor')

def checkStatus():
    status = run('service %s status | grep -o running || echo "stopped"' % (prog))
    if status == 'running':
        return True
    else:
        return False

# Specifies the name of the user under which to run the command
def run_as(user, cmd):
    run('runuser -s /bin/bash %s -c \"%s\"' % (user, cmd))

