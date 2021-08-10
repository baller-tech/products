import Foundation
import baller_common
import baller_kb


/**
 记录用户选择的一项候选词/候选拼音以及对应的符号
 SymbolInfo类为展示流程使用，调用者需要根据自己的业务逻辑重写或修改这些方法
 */
class SymbolInfo: Equatable {
    /**
     候选词/候选拼音对应的符号
     */
    var mSymbol: String = ""
    
    /**
     mSymbol在全部输入符号中的位置
     */
    var mBeginPos = 0
    
    /**
     候选词的中文；候选拼音时不使用
     */
    var mText: String = ""
    
    public static func == (lhs: SymbolInfo, rhs: SymbolInfo) -> Bool {
        return lhs.mBeginPos == rhs.mBeginPos
    }
}

/**
 * 封装了九键输入符号变化时的一些处理流程。
 * NineKeyboardTool类为展示流程使用，调用者需要根据自己的业务逻辑重写或修改这些方法。
 */
class NineKeyboardTool {
    /**
     * 用户输入的原始字符数据
     */
    var mOriginalInputSymbol: String = ""
    
    /**
     * 用户选择且当前正式匹配的候选拼音
     */
    var mMatchingMonosyllable: String = ""
    
    /**
     * 调用put接口时使用的输入数据
     */
    var mInputSymbolForSDKPut: String = ""

    /**
     * 调用put接口时使用的已选中的文字
     */
    var mSelectTextForSDKPut: String = ""

    /**
     * 调用put接口时输入的已匹配的字符
     */
    var mMatchedSymbolForSDKPut: String = ""

    /**
     * 调用syllables接口时使用的输入数据
     */
    var mInputSymbolForSDKSyllable: String = ""

    /**
     * 已选择的候选拼音信息
     */
    var mMonosyllable: [SymbolInfo] = []

    /**
     * 已选择的候选词对应的符号信息
     */
    var mMatchedSymbol: [SymbolInfo] = []
    /**
     * 已选择的候选词对应的符号个数
     */
    var mMatchedSymbolCount = 0
    
    /**
     用户输入了新的符号
     */
    func AddInputSymbol(symbol: String) -> Void
    {
        print("-----------------------------------------press symbol \(symbol)")
        mOriginalInputSymbol += symbol
        ResetInputSymbols()
    }
    
    
    /**
     * 用户输入了回退键
     */
    func InputBackspace() -> Void
    {
        print("-----------------------------------------press backsspace");
        if (mOriginalInputSymbol.isEmpty)
        {
            return;
        }

        mOriginalInputSymbol.removeLast()
        let currentInputSymbolLength = mOriginalInputSymbol.count;

        // 回退键会删除一个已输入的字符，用户选择的候选拼音有可能会作废，需要重新选择。
        // 比如用户输入64，然后选择候选拼音ni，此时输入回退键，相当于用户只输入了一个6，之前的候选拼音ni需要作废。
        for item in mMonosyllable.reversed() {
            if let index = mMonosyllable.lastIndex(of: item) {
                if (item.mBeginPos + item.mSymbol.count < currentInputSymbolLength)
                {
                    break;
                }
                else
                {
                    mMonosyllable.remove(at: index)
                }
            }
        }

        // 回退键会删除一个已输入的字符，用户选择的候选此有可能会作废，需要用户重新进行选择。
        // 比如用户输入644266，然后依次选择候选词“你”、“好”，此时输入两个回退键，相当于用户只输入了6442，之前选择的候选词“好”字作废，需要重新从42开始匹配。
        let inputForSDKSymbolLength = mOriginalInputSymbol.count + mMonosyllable.count
        for item in mMatchedSymbol.reversed() {
            if let index = mMatchedSymbol.lastIndex(of: item) {
                if (item.mBeginPos + item.mSymbol.count < inputForSDKSymbolLength)
                {
                    break;
                }

                mMatchedSymbolCount -= item.mSymbol.count;
                mMatchedSymbol.remove(at: index)
            }
        }
      
        ResetInputSymbols();
    }

    /**
     * 用户选择了一个候选词
     */
    func SelectCandidateWords(select_text: String, matched_symbol: String) -> Void
    {
        print("-----------------------------------------select candidate word \(select_text), \(matched_symbol)");

        if (!mMatchingMonosyllable.isEmpty)
        {
            let end = mMatchingMonosyllable.index(mMatchingMonosyllable.startIndex, offsetBy: matched_symbol.count - 1)
            mMatchingMonosyllable.removeSubrange(mMatchingMonosyllable.startIndex...end)
        }

        let item: SymbolInfo = SymbolInfo();
        item.mBeginPos = mMatchedSymbolCount;
        item.mSymbol = matched_symbol;
        item.mText = select_text;
        mMatchedSymbol.append(item);

        mMatchedSymbolCount += matched_symbol.count;
        ResetInputSymbols();
    }

    /**
     * 用户选择了一个候选拼音
     */
    func SelectMonosyllable(monosyllable: String) -> Void
    {
        print("-----------------------------------------select monosyllable \(monosyllable)");

        mMatchingMonosyllable += monosyllable;
        mMatchingMonosyllable += "'";

        let item: SymbolInfo  = SymbolInfo();
        item.mSymbol = monosyllable;
        item.mBeginPos =  UnmatchedPos();
        mMonosyllable.append(item);

        ResetInputSymbols();
    }
    
    /**
     * 一次任务结束后重置所有数据
     */
    func Reset() -> Void
    {
        mOriginalInputSymbol = "";
        mMatchingMonosyllable = "";
        mInputSymbolForSDKPut = "";
        mInputSymbolForSDKSyllable = "";
        mMatchedSymbolForSDKPut = "";

        mMonosyllable.removeAll();
        mMatchedSymbol.removeAll();
        mMatchedSymbolCount = 0;
    }
    
    /**
     * 获取未匹配字符的起始位置
     */
    func UnmatchedPos() -> Int
    {
        var length = mMatchedSymbolCount;
        for item in mMonosyllable {
            if (item.mBeginPos >= mMatchedSymbolCount) {
                length = item.mBeginPos + item.mSymbol.count;
            }
        }

        return length;
    }

    /**
     * 根据原始输入和已选择的候选词/候选拼音来确定put和syllable接口使用的输入
     */
    func ResetInputSymbols() -> Void
    {
        // 计算put接口输入的符号
        // 再用户输入的字母符号中替换掉用户已选择的候选拼音，每一个候选拼音需要添加分次符
        mInputSymbolForSDKPut = mOriginalInputSymbol
        for item in mMonosyllable.reversed() {
            let start = mInputSymbolForSDKPut.index(mInputSymbolForSDKPut.startIndex, offsetBy: item.mBeginPos)
            let end = mInputSymbolForSDKPut.index(mInputSymbolForSDKPut.startIndex, offsetBy: item.mBeginPos + item.mSymbol.count - 1)
            mInputSymbolForSDKPut.replaceSubrange(start...end, with: item.mSymbol + "'")
        }
        
        // 再用户输入的字母符号中替换掉用户已选择的候选词拼音
        for item in mMatchedSymbol.reversed() {
            let start = mInputSymbolForSDKPut.index(mInputSymbolForSDKPut.startIndex, offsetBy: item.mBeginPos)
            let end = mInputSymbolForSDKPut.index(mInputSymbolForSDKPut.startIndex, offsetBy: item.mBeginPos + item.mSymbol.count - 1)
            mInputSymbolForSDKPut.replaceSubrange(start...end, with: item.mSymbol)
        }

        mSelectTextForSDKPut = "";
        mMatchedSymbolForSDKPut = "";
        for item in mMatchedSymbol {
            mSelectTextForSDKPut += item.mText;
            mMatchedSymbolForSDKPut += item.mSymbol;
        }

        // 计算syllable接口输入的符号
        // syllable输入的符号为未匹配的用户输入符号
        mInputSymbolForSDKSyllable = mInputSymbolForSDKPut
        let remove_charaset_count = mMatchedSymbolForSDKPut.count + mMatchingMonosyllable.count
        if remove_charaset_count > 0 {
            let end = mInputSymbolForSDKSyllable.index(mInputSymbolForSDKSyllable.startIndex, offsetBy: remove_charaset_count - 1)
            mInputSymbolForSDKSyllable.removeSubrange(mInputSymbolForSDKSyllable.startIndex...end)
            
            // 如果未匹配的用户输入符号是分词符则跳过
            while (!mInputSymbolForSDKSyllable.isEmpty && mInputSymbolForSDKSyllable.starts(with: "\'")) {
                mInputSymbolForSDKSyllable.removeFirst();
            }
        }
    }
}

func MatchCallback(user_param: UnsafeMutableRawPointer?, matched_text: UnsafePointer<UInt16>?, matched_symbol: UnsafePointer<UInt16>?) -> Void
{
    var text_count = 0;
    while true {
        if matched_text?[text_count] == 0
        {
            break
        }
        else
        {
            text_count += 1
        }
    }
    
    var symbol_count = 0;
    while true {
        if matched_symbol?[symbol_count] == 0
        {
            break
        }
        else
        {
            symbol_count += 1
        }
    }
    print("\(String(utf16CodeUnits: matched_text!, count: text_count)), \(String(utf16CodeUnits: matched_symbol!, count: symbol_count))")
}

func SyllableCallback(user_param: UnsafeMutableRawPointer?, next_syllable: UnsafePointer<UInt16>?) -> Void
{
    var text_count = 0;
    while true {
        if next_syllable?[text_count] == 0
        {
            break
        }
        else
        {
            text_count += 1
        }
    }
    print("\(String(utf16CodeUnits: next_syllable!, count: text_count))")
}

func AssociateCallback(user_param: UnsafeMutableRawPointer?, suffix_word: UnsafePointer<UInt16>?) -> Void
{
    var text_count = 0;
    while true {
        if suffix_word?[text_count] == 0
        {
            break
        }
        else
        {
            text_count += 1
        }
    }
    print("\(String(utf16CodeUnits: suffix_word!, count: text_count))")
}

class BallerKBDemo {
    let mOrgId: Int64 = 1178599239699136513;
    let mAppId: Int64 = 1213659830391144455;
    let mAppKey: String = "4c8796d641c85643bab9c26b089bad62";
    
    let mLicenseFile = "license0151/baler_sdk.license";
    let mLogPath = "baller_log";
    let mLogLevel = "debug";
    
    let mSystemDataFile = "data/finger_kb.dat";
    let mUserDataFile = "mUserData.dat";
    
    var mBoxDocumentDir = ""
    var mSessionId: Int64 = 0;
    var mNineKeyboardTool: NineKeyboardTool = NineKeyboardTool()
    
    var mUserData: UnsafeMutablePointer<CChar>? = nil
    var mUserDataLen = 0
    
    let mTest9 = true
    let mTest26 = true
    let mTestWithoutUserData = false
    let mTestWithUserData = true
    
    /**
     开始测试的入口函数
     */
    static func StartRun() -> Void {
        let demo = BallerKBDemo()
        if BALLER_SUCCESS.rawValue != demo.Login() {
            print("demo login failed")
            return;
        }
        
        if demo.mTestWithoutUserData {
            RunWithoutUserData(kb_demo: demo)
            if nil != demo.mUserData {
                demo.mUserData!.deallocate()
                demo.mUserDataLen = 0
            }
        }

        if demo.mTestWithUserData {
            RunWithUserData(kb_demo: demo)
            if nil != demo.mUserData {
                demo.mUserData!.deallocate()
                demo.mUserDataLen = 0
            }
        }
        
        if BALLER_SUCCESS.rawValue != demo.Logout()
        {
            print("demo logout failed")
            return;
        }
        
        print("test finished")
    }
    
    /**
     测试不使用用户字典的情况
     */
    static func RunWithoutUserData(kb_demo: BallerKBDemo) -> Void {
        if BALLER_SUCCESS.rawValue != kb_demo.SessionBegin(UserDataEnable: false) {
            print("demo session begin falied")
            return;
        }
        
        // 测试9宫格
        TestKeyboard9(kb_demo: kb_demo)
        
        // 测试26键
        testKeyboard26(kb_demo: kb_demo)
        
        if BALLER_SUCCESS.rawValue != kb_demo.SessionEnd() {
            print("demo session end falied")
            return;
        }
    }
    
    /**
        测试使用用户词典且当前没有用户词典的情况
     */
    static func RunWithUserData(kb_demo: BallerKBDemo) -> Void {
        if BALLER_SUCCESS.rawValue != kb_demo.SessionBegin(UserDataEnable: true) {
            print("demo session begin falied")
            return;
        }
        
        // 测试9宫格
        TestKeyboard9(kb_demo: kb_demo)
        
        // 测试26键
        testKeyboard26(kb_demo: kb_demo)
        
        // 模拟用户选择了“你嚎”这个词，现在要将该词添加到用户数据中。该步骤不会将新增的用户数据保存到文件中。
        if BALLER_SUCCESS.rawValue != kb_demo.SessionCommit(commit_word: "你嚎", write_to_file: 0) {
            print("demo sessioin commit failed")
        }
        
        // 将新增的用户数据保存到文件中。
        if BALLER_SUCCESS.rawValue != kb_demo.SessionCommit(commit_word: "", write_to_file: 1) {
            print("demo sessioin commit failed")
        }

        // 测试26键
        testKeyboard26(kb_demo: kb_demo)

        if BALLER_SUCCESS.rawValue != kb_demo.SessionEnd() {
            print("demo session end falied")
            return;
        }
    }
    
    /**
    测试9宫格候选拼音
    这里模拟用户在输入过程会选择候选拼音并最终输入“淘气”的流程。
     */
    static func TestKeyboard9(kb_demo: BallerKBDemo) -> Void {
        if !kb_demo.mTest9 {
            return
        }

        kb_demo.mNineKeyboardTool.Reset()
        repeat {
            // 输入8
            kb_demo.mNineKeyboardTool.AddInputSymbol(symbol: "8")
            if BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo) {
                break
            }
 
            // 输入2
            kb_demo.mNineKeyboardTool.AddInputSymbol(symbol: "2")
            if BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo) {
                break
            }
            
            // 输入6
            kb_demo.mNineKeyboardTool.AddInputSymbol(symbol: "6")
            if BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo) {
                break
            }
            
            // 输入7
            kb_demo.mNineKeyboardTool.AddInputSymbol(symbol: "7")
            if BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo) {
                break
            }
 
            // 获取所有的匹配结果
            if BALLER_SUCCESS.rawValue != kb_demo.GetFullMatchResult(kb_demo: kb_demo) {
                break
            }
            
            // 选择候选拼音tao
            kb_demo.mNineKeyboardTool.SelectMonosyllable(monosyllable: "tao");
            if (BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo))
            {
                break;
            }

            // 选择候选词淘
            kb_demo.mNineKeyboardTool.SelectCandidateWords(select_text: "淘", matched_symbol: "tao'");
            if (BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo))
            {
                break;
            }

            // 输入4
            kb_demo.mNineKeyboardTool.AddInputSymbol(symbol: "4");
            if (BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo))
            {
                break;
            }
            
            // 回退
            kb_demo.mNineKeyboardTool.InputBackspace();
            if (BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo))
            {
                break;
            }
            
            // 回退
            kb_demo.mNineKeyboardTool.InputBackspace();
            if (BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo))
            {
                break;
            }
            
            // 回退
            kb_demo.mNineKeyboardTool.InputBackspace();
            if (BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo))
            {
                break;
            }
            
            // 回退
            kb_demo.mNineKeyboardTool.InputBackspace();
            if (BALLER_SUCCESS.rawValue != kb_demo.NineInput(kb_demo: kb_demo))
            {
                break;
            }
 
        } while (false);
        
        // 获取“淘气”的联想结果
        print("获取\"淘气\"的联想词");
        if BALLER_SUCCESS.rawValue != kb_demo.SessionAssociate(prefix_word: "淘气") {
            print("call associate failed");
        }
    }
    
    // 测试26格全键盘，用户输入你好，对应按键你好
    static func testKeyboard26(kb_demo: BallerKBDemo) -> Void
    {
        if !kb_demo.mTest26 {
            return
        }

        var inputSymbol: String = "";
        var selectedText: String = "";
        var matchSymbol: String = "";

        repeat {
            // 模拟输入n
            inputSymbol += "n";
            if BALLER_SUCCESS.rawValue != kb_demo.SessionPut(input_symbol: inputSymbol, selected_text: selectedText, matched_symbol: matchSymbol) {
                break;
            }
   
            // 模拟继续输入i
            inputSymbol += "i";
            if BALLER_SUCCESS.rawValue != kb_demo.SessionPut(input_symbol: inputSymbol, selected_text: selectedText, matched_symbol: matchSymbol) {
                break;
            }

            // 模拟继续输入h
            inputSymbol += "h";
            if BALLER_SUCCESS.rawValue != kb_demo.SessionPut(input_symbol: inputSymbol, selected_text: selectedText, matched_symbol: matchSymbol) {
                break;
            }

            // 模拟用户选中匹配文字“你”
            selectedText += "你";
            matchSymbol += "ni";
            if BALLER_SUCCESS.rawValue != kb_demo.SessionPut(input_symbol: inputSymbol, selected_text: selectedText, matched_symbol: matchSymbol) {
                break;
            }

            // 模拟继续输入a
            inputSymbol += "a";
            if BALLER_SUCCESS.rawValue != kb_demo.SessionPut(input_symbol: inputSymbol, selected_text: selectedText, matched_symbol: matchSymbol) {
                break;
            }

            // 模拟继续输入o
            inputSymbol += "o";
            if BALLER_SUCCESS.rawValue != kb_demo.SessionPut(input_symbol: inputSymbol, selected_text: selectedText, matched_symbol: matchSymbol) {
                break;
            }

        } while (false);

        // 测试xinjaing的情况
        inputSymbol = "xinjaing";
        selectedText = "";
        matchSymbol = "";
        if BALLER_SUCCESS.rawValue != kb_demo.SessionPut(input_symbol: inputSymbol, selected_text: selectedText, matched_symbol: matchSymbol) {
            return;
        }


        // 测试xinjaing的情况
        inputSymbol = "xinjavng";
        selectedText = "";
        matchSymbol = "";
        if BALLER_SUCCESS.rawValue != kb_demo.SessionPut(input_symbol: inputSymbol, selected_text: selectedText, matched_symbol: matchSymbol) {
            return;
        }

        // 获取“淘气”的联想结果
        print("获取\"淘气\"的联想词");
        if BALLER_SUCCESS.rawValue != kb_demo.SessionAssociate(prefix_word: "淘气") {
            print("call associate failed");
        }
    }
    
    func NineInput(kb_demo : BallerKBDemo) -> Int32
    {
        var errorCode = kb_demo.SessionPut(input_symbol: mNineKeyboardTool.mInputSymbolForSDKPut, selected_text: mNineKeyboardTool.mSelectTextForSDKPut, matched_symbol: mNineKeyboardTool.mMatchedSymbolForSDKPut)
        if (BALLER_SUCCESS.rawValue != errorCode) {
            print("call put failed \(errorCode)");
            return errorCode
        }
        
        if (!mNineKeyboardTool.mInputSymbolForSDKSyllable.isEmpty)
        {
            errorCode = kb_demo.SessionSyllable(input_symbol: mNineKeyboardTool.mInputSymbolForSDKSyllable);
            if BALLER_SUCCESS.rawValue != errorCode {
                print("call syllable failed \(errorCode)");
            }
        }

        return errorCode;
    }
    
    /**
     获取所有的候选词
     */
    func GetFullMatchResult(kb_demo: BallerKBDemo) -> Int32 {
        var errorCode: Int32 = 0
        repeat {
            errorCode = kb_demo.SessionMore()
        } while (BALLER_MORE_RESULT.rawValue == errorCode)

        if (BALLER_SUCCESS.rawValue != errorCode) {
            print("get full matched result failed \(errorCode)")
            return errorCode
        }
   
        print("get full matched result success")
        return errorCode
    }
    
    /**
     调用SDK的BallerLogin接口
     */
    func Login() -> Int32 {
        let paths = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)
        mBoxDocumentDir = paths[0]
        
        let login_param: String = "org_id=" + String(mOrgId)
            + ",app_id=" + String(mAppId)
            + ",app_key=" + mAppKey
            + ",license=" + mBoxDocumentDir + "/" + mLicenseFile
            + ",log_path=" + mBoxDocumentDir + "/" + mLogPath + ",log_level=" + mLogLevel
        ;
        
        print("begin call BallerLogin with param \(login_param)")
        let errorCode = BallerLogin(login_param)
        if BALLER_SUCCESS.rawValue != errorCode {
            print("call BallerLogin failed \(errorCode)")
            return errorCode
        }
        
        print("call BallerLogin success")
        return errorCode
    }
    
    /**
     调用SDK的BallerLogout接口
     */
    func Logout() -> Int32 {
        print("begin call BallerLogout");
        let errorCode = BallerLogout()
        if BALLER_SUCCESS.rawValue != errorCode {
            print("call BallerLogout failed \(errorCode)")
            return errorCode
        }
        
        print("call BallerLogout success")
        return errorCode
    }
    
    /**
     调用BallerKBSessionBegin接口
     */
    func SessionBegin(UserDataEnable: Bool) -> Int32 {
        var session_param = "language=chs"

        // 获取系统数据文件在bundle中的路径
        let htmlBundlePath = Bundle.main.path(forResource:"baller_bundle", ofType:"bundle")
        let htmlBundle = Bundle.init(path: htmlBundlePath!)
        let path = htmlBundle!.path(forResource:"finger_kb", ofType:"dat", inDirectory:"data")
        if nil == path {
            print("can't find system data in bundle")
            return -1
        }
        // 读取系统数据文件
        let SystemFileData = FileManager.default.contents(atPath: path!)
        if nil == SystemFileData {
            print("system data in nil")
            return -1;
        }
        let SystemFileNSData = SystemFileData! as NSData
        let system_data = SystemFileNSData.bytes.assumingMemoryBound(to: CChar.self)
        
        // 读取用户数据文件
        if UserDataEnable {
            session_param += (", user_data_file=" + mBoxDocumentDir + "/" + mUserDataFile)
        }
        print("begin call BallerKBSessionBegin with para \(session_param)")
        let errorCode = BallerKBSessionBegin(session_param,
                                              system_data, Int32(SystemFileNSData.count),
                                              &mSessionId)
        if BALLER_SUCCESS.rawValue != errorCode {
            print("call BallerKBSessionBegin failed \(errorCode)")
            return errorCode
        }

        print("call BallerKBSessionBegin success")
        return errorCode
    }
    
    /**
     调用BallerKBSessioinEnd接口
     */
    func SessionEnd() -> Int32 {
        print("begin call BallerKBSessionEnd")
        let errorCode = BallerKBSessionEnd(mSessionId)
        if BALLER_SUCCESS.rawValue != errorCode {
            print("call BallerKBSessionEnd failed \(errorCode)")
            return errorCode
        }
        
        print("call BallerKBSessionEnd success")
        return errorCode
    }
    
    /**
        调用BallerKBPut接口
     */
    func SessionPut(input_symbol: String, selected_text: String, matched_symbol: String) -> Int32 {
        print("input:" + input_symbol + " select:" + selected_text + " match:" + matched_symbol);
        
        let start_time = CFAbsoluteTimeGetCurrent()
        
        var input_symbol_utf16_code = Array(input_symbol.utf16)
        input_symbol_utf16_code.append(0)
        var selected_text_utf16_code = Array(selected_text.utf16)
        selected_text_utf16_code.append(0)
        var matched_symbol_utf16_code = Array(matched_symbol.utf16)
        matched_symbol_utf16_code.append(0)
        
        print("begin call BallerKBPut")
        let errorCode = BallerKBPut(mSessionId, "",
                                 input_symbol_utf16_code, selected_text_utf16_code, matched_symbol_utf16_code,
                                 MatchCallback,
                                 nil)
        if BALLER_SUCCESS.rawValue != errorCode {
            print("call BallerKBPut failed \(errorCode)")
            return errorCode
        }
        print("call BallerKBPut success use \((CFAbsoluteTimeGetCurrent() - start_time) * 1000) ms")
       
        return errorCode
    }
    
    /**
        调用BallerKBSyllable接口，获取九宫格的候选拼音
     */
    func SessionSyllable(input_symbol: String) -> Int32 {
        print("获取候选拼音: " + input_symbol);
        
        var input_symbol_utf16_code = Array(input_symbol.utf16)
        input_symbol_utf16_code.append(0)
        
        print("begin call BallerKBSyllable")
        let errorCode = BallerKBSyllable(mSessionId, input_symbol_utf16_code, SyllableCallback, nil)
        if BALLER_SUCCESS.rawValue != errorCode {
            print("call BallerKBSyllable failed \(errorCode)")
            return errorCode
        }
        
        print("call BallerKBSyllable success")
        return errorCode
    }

    /**
        调用BallerKBAssociate接口，获取联想词
     */
    func SessionAssociate(prefix_word: String) -> Int32 {
        var prefix_word_utf16_code = Array(prefix_word.utf16)
        prefix_word_utf16_code.append(0)
        
        print("begin call BallerKBAssociate")
        let errorCode = BallerKBAssociate(mSessionId, prefix_word_utf16_code, AssociateCallback, nil)
        if BALLER_SUCCESS.rawValue != errorCode {
            print("call BallerKBAssociate failed \(errorCode)")
            return errorCode
        }
        
        print("call BallerKBAssociate success")
        return errorCode
    }
    
    /**
        调用BallerKBMore接口，
     */
    func SessionMore() -> Int32 {
        print("begin call BallerKBMore")
        let errorCode = BallerKBMore(mSessionId, MatchCallback, nil)
        if BALLER_SUCCESS.rawValue != errorCode && BALLER_MORE_RESULT.rawValue != errorCode {
            print("call BallerKBMore failed \(errorCode)")
            return errorCode
        }
        
        print("call BallerKBMore success")
        return errorCode
    }
    
    /**
        调用BallerKBAssociate接口，
     */
    func SessionCommit(commit_word: String, write_to_file: Int32) -> Int32 {
        var commit_word_utf16_code = Array(commit_word.utf16)
        commit_word_utf16_code.append(0)
        
        print("begin call BallerKBCommit")
        let errorCode = BallerKBCommit(mSessionId, commit_word_utf16_code, write_to_file)
        if BALLER_SUCCESS.rawValue != errorCode {
            print("call BallerKBCommit failed \(errorCode)")
            return errorCode
        }
        
        print("call BallerKBCommit success")
        return errorCode
    }
}
