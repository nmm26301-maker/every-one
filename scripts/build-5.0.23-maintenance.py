"""Build 5.0.23 guided offline listening and speaking from original-signed 5.0.22."""
import importlib.util
import pathlib

if __name__ == '__main__':
    spec = importlib.util.spec_from_file_location('maintenance', pathlib.Path(__file__).with_name('build-5.0.16-maintenance.py'))
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    builder.BASE_SHA = 'd608ae2cce053bf083b5f9d97fef546265ed358ff4540e70a9265937718db05b'
    builder.VERSION_NAME = '5.0.23'
    builder.VERSION_CODE = 82
    builder.BUILD_DIR = 'maintenance523'
    builder.EXTRA_ASSETS = tuple('journey/course-audio/'+name for name in
        ['level'+str(i)+'.zip' for i in range(6)]+['index.json','NOTICE.txt','MODEL_CARD.txt'])
    builder.__doc__ = __doc__
    builder.main()
