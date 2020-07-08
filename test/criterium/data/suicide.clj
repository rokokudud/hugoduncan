(ns criterium.data.suicide
  "lengths (in days) of psychiatric treatment spells for patients

  Sourced from https://rdrr.io/cran/bde/man/suicide.r.html

  Silverman, B. (1986). Density Estimation for Statistics and Data Analysis. Chapman &
  Hall

  Copas, J. B. and Fryer, M. J. (1980). Density estimation and suicide risks in
  psychiatric treatment. Journal of the Royal Statistical Society. Series A, 143(2),
  167-176")


(def days-of-treatment
  "The dataset comprises lengths (in days) of psychiatric treatment spells for patients
  used as controls in a study of suicide risks. The data have been scaled to the
  interval [0,1] by dividing each data sample by the maximum value."
  [0.001356852 0.001356852 0.001356852 0.006784261 0.009497965 0.010854817
   0.010854817 0.017639077 0.018995929 0.018995929 0.023066486 0.024423338
   0.028493894 0.028493894 0.029850746 0.033921303 0.036635007 0.036635007
   0.040705563 0.040705563 0.042062415 0.042062415 0.043419267 0.046132972
   0.047489824 0.048846676 0.050203528 0.051560380 0.052917232 0.052917232
   0.054274084 0.066485753 0.066485753 0.073270014 0.075983718 0.075983718
   0.084124830 0.085481682 0.088195387 0.088195387 0.090909091 0.101763908
   0.103120760 0.107191316 0.111261872 0.112618725 0.113975577 0.113975577
   0.113975577 0.122116689 0.123473541 0.124830393 0.126187246 0.126187246
   0.139755767 0.139755767 0.150610583 0.151967436 0.161465400 0.165535957
   0.166892809 0.170963365 0.175033921 0.181818182 0.195386703 0.199457259
   0.207598372 0.221166893 0.226594301 0.237449118 0.309362280 0.313432836
   0.318860244 0.328358209 0.347354138 0.347354138 0.348710991 0.421981004
   0.426051560 0.436906377 0.500678426 0.563093623 0.777476255 0.826322931
   0.868385346 1.000000000])
