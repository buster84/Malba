import sys, os, re , string , random , time
from fabric.api import *
from fabric.contrib.files import exists

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
env.user     = getenv('SSH_USER')
env.password = getenv('SSH_PASSWORD')
prog         = getenv('SERVICE_NAME')
run_user     = getenv('RUN_USER')
artifact_url = getenv('PACKAGE_URL')
port         = getenv('PORT')
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
    if isAvailable():
        stop()

    update()
    proc_check()
    start()
    create_monit()
    checkStatus()

def isAvailable():
    return exists(pidfile)

def stop():
    run_as(run_user, 'kill $(cat %s)' % (pidfile))
    time.sleep(10.0)

def update():
    workspace    = '/tmp/%s_%s' % (prog, randstrings())
    run('mkdir -p %s' % (workspace))
    with cd(workspace):
        run('wget %s' % (artifact_url))
        run('rpm -qa | grep %s || yum install -y ./*rpm' % ( prog ))
        run('yum update -y ./*rpm')
    run('rm -rf %s' % (workspace))

def start():
    run('mkdir -p %s' % (piddir))
    run('chown -R %s %s' % (run_user, piddir))

    data = {"start_script"     : start_script,
            "pidfile"          : pidfile,
            "port"             : port,
            "application_conf" : "application_" + mode + ".conf",
            "mode"             : mode,
            "newrelic_jar"     : "/usr/share/" + run_user + "/lib/newrelic.jar",
            "newrelic_conf"    : "/usr/share/" + run_user + "/conf/newrelic.yml",
            "logger_conf"      : "logger_" + mode + ".xml",
            "run_user"         : run_user,
            "java_home"        : java_home }
    with shell_env(AKKA_PORT=akka_port, SELF_IP=env.host):
        run_as(run_user, 'JAVA_HOME=%(java_home)s /usr/bin/nohup %(start_script)s -Dpidfile.path=%(pidfile)s -Dhttp.port=%(port)s -Dconfig.resource=%(application_conf)s -Dlogger.resource=%(logger_conf)s -Djsse.enableSNIExtension=false 1>>/var/log/%(run_user)s/nohup.out 2>>/var/log/%(run_user)s/nohup_error.log &' % data)


def restart():
    change_balancer('disable')
    if isAvailable():
        stop()

    proc_check()
    start()
    checkStatus()
    change_balancer('enable')


def proc_check():
    time.sleep(20.0)

def get_balancer_list():
    local_filename = '/tmp/%s_%s' % (prog , randstrings())
    get(balancer_list_file,local_filename)
    try:
        list = open(local_filename, "r")
    except IOError:
        print 'get remote server balancer_config [%s] load Error' % (balancer_list_file)
        sys.exit(1)

    os.remove(local_filename)
    return list

def change_balancer(mode):
    balancer_list = get_balancer_list()
    for host in balancer_list:
        run('/home/public/shanon.co.jp/admintool/mod_proxy_balancer_changer/bl_changer.pl --domain ' + host.rstrip() + ' --myself --' + mode)

def create_monit():
   # TODO
   time.sleep(1.0)

def checkStatus():
    status_url    = 'http://%s:%s/%s/check_alive' % (env.host, port, run_user)

    status = 'ko'
    print status_url
    for i in range(1, 20):
        if 'ko' != status:
            break
        try:
            status = run('curl %s' % (status_url))
        except:
            print 'status is not ok'
            status = 'ko'
        time.sleep(10.0)
    print status
    print 'status is ok'

# Specifies the name of the user under which to run the command
def run_as(user, cmd):
    run('runuser -s /bin/bash %s -c \"%s\"' % (user, cmd))

