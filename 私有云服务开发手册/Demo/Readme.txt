1. 本例程仅为了展示如何调用北京大牛儿科技发展有限公司私有云的接口，仅供参考，不能再实际项目中使用
2. 本例程所有代码依赖python 3.7版本，callback_service.py依赖Tornado，sample_asr.py/sample_asr.py 依赖requests
3. callback_service.py 是使用回调机制获取识别结果时的一个http服务器Demo，依赖Tornado。如果使用轮询的机制则不需要参考此文件

4. ASR 的示例运行
    4.1 轮询获取结果方式
        4.1.1 根据自己的环境修改 url/pcm_file_path变量，将is_use_callback设置为False
        4.1.2 运行sample_asr.py示例
        4.1.3 识别结果会打印在控制台中
    4.2 使用回调的方式获取识别结果
        4.2.1 根据自己的环境修改 url/callback_url/pcm_file_path变量，将is_use_callback设置为True
        4.2.2 运行callback_service.py,启动回调的web服务
        4.2.3 运行sample_asr.py示例
        4.2.4 在回调的web服务中，会打印出识别的结果
        4.2.5 回调的地址需保证私有云服务器可以访问

5. TTS 的示例运行
    5.1 轮询获取结果方式
        5.1.1 根据自己的环境修改 url/test_txt变量，将is_use_callback设置为False
        5.1.2 运行sample_tts.py示例
        5.1.3 合成结果会保存在当前路径下以请求id命名的pcm文件
    5.2 使用回调的方式获取识别结果
        4.2.1 根据自己的环境修改 url/callback_url/test_txt变量，将is_use_callback设置为True
        4.2.2 运行callback_service.py,启动回调的web服务
        4.2.3 运行sample_tts.py示例
        4.2.4 在回调的web服务中，会将合成结果保存在当前路径下以请求id命名的pcm文件
        4.2.5 回调的地址需保证私有云服务器可以访问
