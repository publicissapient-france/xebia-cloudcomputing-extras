package fr.xebia.cloud.cloudinit;
/*
 * Copyright 2008-2010 Xebia and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class FreemarkerUtils {

    /**
     * 
     * @param rootMap
     *            root node of the freemarker datamodel.
     * @param templatePath
     *            classpath of the template
     * @return generated file
     */
    @SuppressWarnings("unchecked")
    @Nonnull
    public static String generate(@Nullable Map<String, Object> rootMap, @Nonnull String templatePath) {
        Preconditions.checkNotNull(templatePath, "'templatePath' can NOT be null");
        rootMap = (Map<String, Object>) Objects.firstNonNull(rootMap, Collections.emptyMap());

        try {
            Configuration cfg = new Configuration();
            cfg.setClassForTemplateLoading(FreemarkerUtils.class, "/");

            Template template = cfg.getTemplate(templatePath);
            Writer out = new StringWriter();
            template.process(rootMap, out);
            return out.toString();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } catch (TemplateException e) {
            throw Throwables.propagate(e);
        }
    }

}
