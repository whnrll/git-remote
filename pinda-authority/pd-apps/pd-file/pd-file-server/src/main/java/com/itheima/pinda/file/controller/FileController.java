package com.itheima.pinda.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.itheima.pinda.base.BaseController;
import com.itheima.pinda.base.R;
import com.itheima.pinda.dozer.DozerUtils;
import com.itheima.pinda.file.dto.FilePageReqDTO;
import com.itheima.pinda.file.dto.FileUpdateDTO;
import com.itheima.pinda.file.dto.FolderDTO;
import com.itheima.pinda.file.dto.FolderSaveDTO;
import com.itheima.pinda.file.entity.File;
import com.itheima.pinda.file.manager.FileRestManager;
import com.itheima.pinda.file.service.FileService;
import com.itheima.pinda.log.annotation.SysLog;
import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * 文件前端控制器
 */
@Validated
@RestController
@RequestMapping("/file")
@Slf4j
@Api(value = "文件表", tags = "文件表")
public class FileController extends BaseController {
    @Autowired
    private FileService fileService;
    @Autowired
    private FileRestManager fileRestManager;
    @Autowired
    private DozerUtils dozerUtils;

    /**
     * 查询单个文件信息
     *
     * @param id
     * @return
     */
    @ApiOperation(value = "查询文件", notes = "查询文件")
    @GetMapping
    public R<File> get(@RequestParam(value = "id") Long id) {
        File file = fileService.getById(id);
        if (file != null && file.getIsDelete()) {
            return success(null);
        }
        return success(file);
    }

    /**
     * 获取文件分页
     */
    @ApiOperation(value = "分页查询文件", notes = "获取文件分页")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "current", value = "当前页", dataType = "long", paramType = "query", defaultValue = "1"),
            @ApiImplicitParam(name = "size", value = "每页显示几条", dataType = "long", paramType = "query", defaultValue = "10"),
    })
    @GetMapping(value = "/page")
    public R<IPage<File>> page(FilePageReqDTO data) {
        return success(fileRestManager.page(getPage(), data));
    }

    /**
     * 上传文件
     */
    @ApiOperation(value = "上传文件", notes = "上传文件 ")
    @ApiResponses({
            @ApiResponse(code = 60102, message = "文件夹为空"),
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "source", value = "文件来源", dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "userId", value = "用户id", dataType = "long", paramType = "query"),
            @ApiImplicitParam(name = "folderId", value = "文件夹id", dataType = "long", paramType = "query"),
            @ApiImplicitParam(name = "file", value = "附件", dataType = "MultipartFile", allowMultiple = true, required = true),
    })
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public R<File> upload(
            @NotNull(message = "文件夹不能为空")
            @RequestParam(name = "source", defaultValue = "inner") String source,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(value = "folderId") Long folderId,
            @RequestParam(value = "file") MultipartFile simpleFile) {
        //1，先将文件存在本地,并且生成文件名
        log.info("contentType={}, name={} , sfname={}", simpleFile.getContentType(), simpleFile.getName(), simpleFile.getOriginalFilename());
        // 忽略路径字段,只处理文件类型
        if (simpleFile.getContentType() == null) {
            return fail("文件为空");
        }

        File file = fileService.upload(simpleFile, folderId, source, userId);

        return success(file);
    }


    /**
     * 保存文件夹
     */
    @ApiResponses({
            @ApiResponse(code = 60000, message = "文件夹为空"),
            @ApiResponse(code = 60001, message = "文件夹名称为空"),
            @ApiResponse(code = 60002, message = "父文件夹为空"),
    })
    @ApiOperation(value = "新增文件夹", notes = "新增文件夹")
    @RequestMapping(value = "", method = RequestMethod.POST)
    public R<FolderDTO> saveFolder(@Valid @RequestBody FolderSaveDTO folderSaveDto) {
        //2，获取身份

        FolderDTO folder = fileService.saveFolder(folderSaveDto);
        return success(folder);
    }

    /**
     * 修改文件、文件夹信息
     *
     * @param fileUpdateDTO
     * @return
     */
    @ApiOperation(value = "修改文件/文件夹名称", notes = "修改文件/文件夹名称")
    @ApiResponses({
            @ApiResponse(code = 60100, message = "文件为空"),
    })
    @RequestMapping(value = "", method = RequestMethod.PUT)
    public R<Boolean> update(@Valid @RequestBody FileUpdateDTO fileUpdateDTO) {
        // 判断文件名是否有 后缀
        if (StringUtils.isNotEmpty(fileUpdateDTO.getSubmittedFileName())) {
            File oldFile = fileService.getById(fileUpdateDTO.getId());
            if (oldFile.getExt() != null && !fileUpdateDTO.getSubmittedFileName().endsWith(oldFile.getExt())) {
                fileUpdateDTO.setSubmittedFileName(fileUpdateDTO.getSubmittedFileName() + "." + oldFile.getExt());
            }
        }
        File file = dozerUtils.map2(fileUpdateDTO, File.class);

        fileService.updateById(file);
        return success(true);
    }

    /**
     * 根据Ids进行文件删除
     *
     * @param ids
     * @return
     */
    @ApiOperation(value = "根据Ids进行文件删除", notes = "根据Ids进行文件删除  ")
    @DeleteMapping(value = "/ids")
    public R<Boolean> removeList(@RequestParam(value = "ids[]") Long[] ids) {
        Long userId = getUserId();
        return success(fileService.removeList(userId, ids));
    }

    /**
     * 下载一个文件或多个文件打包下载
     *
     * @param ids
     * @param response
     * @throws Exception
     */
    @ApiOperation(value = "下载一个文件或多个文件打包下载", notes = "下载一个文件或多个文件打包下载")
    @GetMapping(value = "/download", produces = "application/octet-stream")
    public void download(
            @ApiParam(name = "ids[]", value = "文件id 数组")
            @RequestParam(value = "ids[]") Long[] ids,
            HttpServletRequest request, HttpServletResponse response) throws Exception {
        fileRestManager.download(request, response, ids, null);
    }

}