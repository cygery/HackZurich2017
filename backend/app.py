from __future__ import print_function

import os
import subprocess
import pickle
import requests
from flask import Flask, request, redirect, url_for, flash
from werkzeug.utils import secure_filename
import json
import hashlib
from threading import Thread

UPLOAD_FOLDER = 'REDACTED'
ALLOWED_EXTENSIONS = set(['jpg', 'jpeg'])

app = Flask(__name__)
app.secret_key = 'REDACTED'
FIREBASE_KEY = 'REDACTED'
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

PROJECT_DIR = '/home/ubuntu/isbi2017-part3'
CACHE_FILE = 'cache.pkl'
cache = {}

def load_cache():
    global cache

    try:
        with open(CACHE_FILE, 'rb') as f:
            cache = pickle.load(f)
    except EnvironmentError:
        pass

def store_cache():
    with open(CACHE_FILE, 'wb') as f:
         pickle.dump(cache, f, pickle.HIGHEST_PROTOCOL)

def notify_client(filename, token, res):
    r = requests.post('https://fcm.googleapis.com/fcm/send', data={
        'to': token,
        'data': json.dumps({
            'filename': filename,
            'prob': res
        })
    }, headers={
        'Authorization': 'key={}'.format(FIREBASE_KEY)
    })

def process_request(filename, token):
    global cache

    with open(os.path.join(app.config['UPLOAD_FOLDER'], filename), 'rb') as f:
        h = hashlib.md5(f.read()).hexdigest()

    if h in cache:
        notify_client(filename, token, cache[h])
    else:
        basename = filename.split('.')[0]
        with open(os.path.join(PROJECT_DIR, 'data/config.txt'), 'w') as f:
            f.write('image, (case), type, age, has_age, sex, has_sex, (melanoma), (keratosis), (schedule), (weight)\n{}, , d, 30, 1, male, 1, 0, 0, 0, 0'.format(basename))

        subprocess.call(['convert', os.path.join(app.config['UPLOAD_FOLDER'], filename), '-resize', '299x299!', os.path.join(PROJECT_DIR, 'data/images299/{}.jpg'.format(basename))])
        subprocess.call(['convert', os.path.join(app.config['UPLOAD_FOLDER'], filename), '-resize', '224x224!', os.path.join(PROJECT_DIR, 'data/images224/{}.jpg'.format(basename))])
        subprocess.call(['python', os.path.join(PROJECT_DIR, 'datasets/convert_skin_lesions.py'), 'TEST', os.path.join(PROJECT_DIR, 'data/config.txt'), os.path.join(PROJECT_DIR, 'data/images299'), os.path.join(PROJECT_DIR, 'data/test.299.tfr'), os.path.join(PROJECT_DIR, 'data/no-blacklist.txt')])
        subprocess.call(['python', os.path.join(PROJECT_DIR, 'datasets/convert_skin_lesions.py'), 'TEST', os.path.join(PROJECT_DIR, 'data/config.txt'), os.path.join(PROJECT_DIR, 'data/images224'), os.path.join(PROJECT_DIR, 'data/test.224.tfr'), os.path.join(PROJECT_DIR, 'data/no-blacklist.txt')])
        subprocess.call(['rm', '-rf', os.path.join(PROJECT_DIR, 'running/isbitest.features')])
        subprocess.call(['mkdir', os.path.join(PROJECT_DIR, 'running/isbitest.features')])
        subprocess.call([os.path.join(PROJECT_DIR, 'etc/predict_all_component_models_isbi.sh'), os.path.join(PROJECT_DIR, 'data/test.299.tfr'), os.path.join(PROJECT_DIR, 'data/test.224.tfr'), 'test', os.path.join(PROJECT_DIR, 'running/isbitest.features')])
        subprocess.call(['python', os.path.join(PROJECT_DIR, 'etc/assemble_meta_features.py'), 'ALL_LOGITS', os.path.join(PROJECT_DIR, 'running/isbitest.features'), os.path.join(PROJECT_DIR, 'running/isbitest.features/isbitest.metall.features')])
        output = subprocess.check_output(['python', os.path.join(PROJECT_DIR, 'predict_svm_layer.py'), '--input_model', os.path.join(PROJECT_DIR, 'running/svm.models/metall.svm'), '--input_test', os.path.join(PROJECT_DIR, 'running/isbitest.features/isbitest.metall.features'), '--pool_by_id', 'xtrm'])

        res = float(output.split(b',')[1])

        cache[h] = res
        store_cache()

        notify_client(filename, token, res)

@app.route('/')
def index():
    return 'Move along...'

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

@app.route('/analyze', methods=['GET', 'POST'])
def upload_file():
    if request.method == 'POST':
        # check if the post request has the file part
        if 'file' not in request.files:
            flash('No file part')
            return json.dumps({'success':False}), 400, {'ContentType':'application/json'} 
        file = request.files['file']
        if 'token' not in request.form:
            flash('No token')
            return json.dumps({'success':False}), 400, {'ContentType':'application/json'}
        token = request.form['token']
        # if user does not select file, browser also
        # submit a empty part without filename
        if file.filename == '':
            flash('No selected file')
            return json.dumps({'success':False}), 400, {'ContentType':'application/json'} 
        if file and allowed_file(file.filename):
            filename = secure_filename(file.filename)
            file.save(os.path.join(app.config['UPLOAD_FOLDER'], filename))
            t = Thread(target=process_request, args=(filename,token))
            t.start()
            notify_client(filename, token, -1)
            return json.dumps({'success':True}), 200, {'ContentType':'application/json'} 
    return '''
    <!doctype html>
    <title>Upload new File</title>
    <h1>Upload new File</h1>
    <form method=post enctype=multipart/form-data>
      <p><input type=file name=file>
         <input type=submit value=Upload>
    </form>
    '''

if __name__ == '__main__':
    load_cache()
    app.run('0.0.0.0', 8897)
