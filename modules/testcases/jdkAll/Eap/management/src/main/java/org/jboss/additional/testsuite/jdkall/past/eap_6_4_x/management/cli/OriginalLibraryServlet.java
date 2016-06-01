package org.jboss.additional.testsuite.jdkall.past.eap_6_4_x.management.cli;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jboss.eap.additional.testsuite.annotations.EapAdditionalTestsuite;

/**
 * @author Stuart Douglas
 */
@WebServlet(urlPatterns = "/LibraryServlet")
@EapAdditionalTestsuite({"modules/testcases/jdkAll/Eap/management/src/main/java"})
public class OriginalLibraryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.getWriter().write("Original Library Servlet");
    }
}